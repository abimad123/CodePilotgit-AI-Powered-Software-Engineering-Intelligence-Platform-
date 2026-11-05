"""Exercise Stage 1 HTTP flows against a local development database.

Creates random test users and one soft-deleted project. Never prints credentials.
"""

import argparse
import json
import secrets
from concurrent.futures import ThreadPoolExecutor
import urllib.error
import urllib.request


def call(base, method, path, body=None, token=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None


def expect(actual, wanted, label):
    assert actual == wanted, f"{label}: expected HTTP {wanted}, got {actual}"
    print(f"PASS {label}: HTTP {actual}")


def main(base):
    suffix = secrets.token_hex(8)
    password = secrets.token_urlsafe(24)
    a = {"name": "Smoke Owner", "email": f"smoke-owner-{suffix}@example.test", "password": password}
    b = {"name": "Smoke Member", "email": f"smoke-member-{suffix}@example.test", "password": password}
    c = {"name": "Smoke Admin", "email": f"smoke-admin-{suffix}@example.test", "password": password}
    for path in ("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"):
        status, body = call(base, "GET", path)
        expect(status, 200, path)
        assert body["status"] == "UP"
    preflight = urllib.request.Request(base + "/api/v1/projects", method="OPTIONS", headers={
        "Origin": "http://localhost:5173", "Access-Control-Request-Method": "GET"})
    with urllib.request.urlopen(preflight, timeout=15) as response:
        assert response.headers.get("Access-Control-Allow-Origin") == "http://localhost:5173"
    print("PASS allowed CORS origin")
    preflight = urllib.request.Request(base + "/api/v1/projects", method="OPTIONS", headers={
        "Origin": "https://untrusted.example", "Access-Control-Request-Method": "GET"})
    try:
        with urllib.request.urlopen(preflight, timeout=15) as response:
            assert not response.headers.get("Access-Control-Allow-Origin")
    except urllib.error.HTTPError as error:
        assert not error.headers.get("Access-Control-Allow-Origin")
    print("PASS unlisted CORS origin rejected")
    status, _ = call(base, "GET", "/api/v1/users/me")
    expect(status, 401, "unauthenticated profile")
    status, _ = call(base, "GET", "/api/v1/users/me", token="not-a-jwt")
    expect(status, 401, "invalid JWT")
    status, owner = call(base, "POST", "/api/v1/auth/register", a)
    expect(status, 201, "register owner")
    assert "passwordHash" not in owner and "password" not in owner
    status, member = call(base, "POST", "/api/v1/auth/register", b)
    expect(status, 201, "register member")
    status, admin = call(base, "POST", "/api/v1/auth/register", c)
    expect(status, 201, "register admin")
    status, _ = call(base, "POST", "/api/v1/auth/register", a)
    expect(status, 409, "duplicate registration")
    status, _ = call(base, "POST", "/api/v1/auth/register", {"name": "Invalid", "email": "not-an-email", "password": password})
    expect(status, 400, "invalid registration email")
    status, _ = call(base, "POST", "/api/v1/auth/register", {"email": "missing@example.test", "password": password})
    expect(status, 400, "missing registration name")
    status, _ = call(base, "POST", "/api/v1/auth/login", {"email": a["email"], "password": "wrong"})
    expect(status, 401, "invalid login")
    status, owner_tokens = call(base, "POST", "/api/v1/auth/login", {"email": a["email"], "password": password})
    expect(status, 200, "owner login")
    status, member_tokens = call(base, "POST", "/api/v1/auth/login", {"email": b["email"], "password": password})
    expect(status, 200, "member login")
    status, admin_tokens = call(base, "POST", "/api/v1/auth/login", {"email": c["email"], "password": password})
    expect(status, 200, "admin login")
    owner_access = owner_tokens["accessToken"]
    member_access = member_tokens["accessToken"]
    admin_access = admin_tokens["accessToken"]
    status, profile = call(base, "GET", "/api/v1/users/me", token=owner_access)
    expect(status, 200, "current user")
    assert profile["id"] == owner["id"]
    status, project = call(base, "POST", "/api/v1/projects", {"name": "Smoke project"}, owner_access)
    expect(status, 201, "create project")
    assert project["role"] == "OWNER" and project["ownerId"] == owner["id"]
    project_path = f"/api/v1/projects/{project['id']}"
    status, page = call(base, "GET", "/api/v1/projects", token=member_access)
    expect(status, 200, "member project list before assignment")
    assert all(item["id"] != project["id"] for item in page["content"])
    status, _ = call(base, "GET", project_path, token=member_access)
    expect(status, 404, "cross-project detail isolation")
    status, _ = call(base, "PATCH", project_path, {"name": "Denied"}, member_access)
    expect(status, 404, "cross-project update isolation")
    status, _ = call(base, "POST", project_path + "/members", {"userId": member["id"], "role": "MEMBER"}, member_access)
    expect(status, 404, "cross-project membership isolation")
    status, added = call(base, "POST", project_path + "/members", {"userId": member["id"], "role": "MEMBER"}, owner_access)
    expect(status, 201, "owner adds existing member")
    assert added["role"] == "MEMBER"
    status, detail = call(base, "GET", project_path, token=member_access)
    expect(status, 200, "member project detail")
    assert detail["ownerId"] == owner["id"] and detail["name"] == "Smoke project"
    status, visible = call(base, "GET", "/api/v1/projects", token=member_access)
    expect(status, 200, "member project list after assignment")
    assert any(item["id"] == project["id"] for item in visible["content"])
    status, _ = call(base, "POST", project_path + "/members", {"userId": member["id"], "role": "MEMBER"}, owner_access)
    expect(status, 409, "duplicate membership")
    status, _ = call(base, "PATCH", project_path, {"name": "Denied"}, member_access)
    expect(status, 403, "member cannot update")
    status, _ = call(base, "DELETE", project_path, token=member_access)
    expect(status, 403, "member cannot delete")
    status, _ = call(base, "DELETE", project_path + f"/members/{owner['id']}", token=owner_access)
    expect(status, 403, "owner cannot remove self")
    status, _ = call(base, "POST", project_path + "/members", {"userId": admin["id"], "role": "ADMIN"}, owner_access)
    expect(status, 201, "owner assigns admin")
    status, updated = call(base, "PATCH", project_path, {"name": "Updated by admin"}, admin_access)
    expect(status, 200, "admin updates project")
    assert updated["name"] == "Updated by admin" and updated["ownerId"] == owner["id"]
    status, _ = call(base, "DELETE", project_path, token=admin_access)
    expect(status, 403, "admin cannot delete project")
    status, _ = call(base, "DELETE", project_path + f"/members/{owner['id']}", token=admin_access)
    expect(status, 403, "admin cannot remove owner")
    status, _ = call(base, "POST", project_path + "/members", {"userId": member["id"], "role": "ADMIN"}, admin_access)
    expect(status, 403, "admin cannot grant admin role")
    status, refreshed = call(base, "POST", "/api/v1/auth/refresh", {"refreshToken": owner_tokens["refreshToken"]})
    expect(status, 200, "refresh token rotation")
    assert refreshed["refreshToken"] != owner_tokens["refreshToken"]
    status, _ = call(base, "POST", "/api/v1/auth/refresh", {"refreshToken": owner_tokens["refreshToken"]})
    expect(status, 401, "old refresh token rejected")
    status, _ = call(base, "POST", "/api/v1/auth/logout", {"refreshToken": refreshed["refreshToken"]})
    expect(status, 204, "logout")
    status, _ = call(base, "POST", "/api/v1/auth/refresh", {"refreshToken": refreshed["refreshToken"]})
    expect(status, 401, "revoked refresh token rejected")
    with ThreadPoolExecutor(max_workers=2) as pool:
        concurrent = list(pool.map(lambda _: call(base, "POST", "/api/v1/auth/refresh", {"refreshToken": member_tokens["refreshToken"]})[0], range(2)))
    assert sorted(concurrent) == [200, 401], f"concurrent refresh expected one success and one rejection, got {concurrent}"
    print("PASS concurrent refresh: one HTTP 200, one HTTP 401")
    status, _ = call(base, "POST", project_path + "/github/installations/start", {"installationId": 42}, owner_access)
    expect(status, 503, "GitHub unconfigured")
    status, _ = call(base, "DELETE", project_path, token=owner_access)
    expect(status, 204, "owner soft-deletes project")
    status, _ = call(base, "GET", project_path, token=owner_access)
    expect(status, 404, "deleted project hidden")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    main(args.base_url.rstrip("/"))
