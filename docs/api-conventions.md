# API conventions and error standards

These conventions apply to the Stage 1 backend. Authentication, project, health, and error behavior were exercised through the HTTP smoke suite; live GitHub App behavior remains unverified. Future features remain proposals.

## Stage 1 endpoint inventory

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | Create local user. |
| POST | `/api/v1/auth/login` | Public | Return access and refresh tokens. |
| POST | `/api/v1/auth/refresh` | Refresh token in JSON body | Rotate refresh token. |
| POST | `/api/v1/auth/logout` | Refresh token in JSON body | Revoke refresh token. |
| GET | `/api/v1/users/me` | Bearer access token | Safe current-user profile. |
| POST/GET | `/api/v1/projects` | Bearer | Create/list accessible projects. |
| GET/PATCH/DELETE | `/api/v1/projects/{projectId}` | Bearer and project role | Detail/update/soft delete. |
| GET/POST | `/api/v1/projects/{projectId}/members` | Bearer and project role | List/add existing users. |
| DELETE | `/api/v1/projects/{projectId}/members/{userId}` | Bearer and project role | Remove member subject to owner/admin rules. |
| POST | `/api/v1/projects/{projectId}/github/installations/start` | OWNER/ADMIN | Begin GitHub user authorization for an intended installation ID. |
| POST | `/api/v1/projects/{projectId}/github/installations/complete` | Same user and project | Exchange code, verify GitHub user access, persist installation association. |
| GET/POST | `/api/v1/projects/{projectId}/github/repositories` | Member for GET; OWNER/ADMIN for POST | List installation repositories/connect one after validation. Requires `installationId` query for GET. |
| GET | `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness` | Public | Status only, no details. |

Project list currently uses bounded `page` and `size` offset pagination; this differs from the Stage 0 cursor proposal. Cursor pagination can be added when list ordering and scale justify it. The Stage 1 API does not implement asynchronous jobs or `Idempotency-Key` yet.

## Public API

- Base path `/api/v1`; JSON over HTTPS; UTF-8; nouns in plural kebab-case paths, camelCase JSON fields, ISO 8601 UTC timestamps, UUID resource identifiers.
- Authenticate through a secure server-side session or short-lived token. Exact identity provider is an open product decision. Never send GitHub App private keys or provider keys to the browser.
- Authorize each project-scoped request by membership and role before loading content. Return `404` for inaccessible resource IDs where that prevents cross-project enumeration.
- Project listing uses offset pagination with `page` (default 0) and `size` (default 20, maximum 100); GitHub repository listing uses GitHub pages (1-based, 100 items). Future mutable high-volume lists should use cursor pagination.
- Accept `Idempotency-Key` for mutating requests that enqueue work; return the same result for a repeated key in the same project and operation. Use GitHub delivery ID for webhook deduplication.
- Long analyses return `202 Accepted` with an analysis/job resource and `Location`; clients poll or later subscribe to updates. Never hold a public request open for a full repository scan.
- Support `X-Request-Id` from a trusted gateway or generate one; return it in response and logs. Treat client-provided IDs as untrusted and bounded.
- Version internal Spring-to-AI contracts separately, authenticate them with workload identity or a rotated service credential, and enforce timeouts and request limits.

## Error envelope

Use the same shape for validation and operational errors. Do not leak stack traces, SQL, credentials, private source, or untrusted GitHub response bodies.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request could not be accepted.",
    "requestId": "b4a71d88-3c56-4dbd-99db-a6dd96c9dd4f",
    "details": [
      { "field": "repositoryId", "reason": "must be a valid UUID" }
    ]
  }
}
```

| Status | Code examples | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`, `INVALID_CURSOR` | Invalid shape or parameter. |
| 401 | `UNAUTHENTICATED` | Authentication missing or expired. |
| 403 | `FORBIDDEN` | Authenticated but insufficient role for an action. |
| 404 | `NOT_FOUND` | Missing or intentionally hidden resource. |
| 409 | `CONFLICT`, `ALREADY_IN_PROGRESS` | State or idempotency conflict. |
| 413 | `PAYLOAD_TOO_LARGE` | Configured input limit exceeded. |
| 429 | `RATE_LIMITED` | Per-user/project limit; include `Retry-After`. |
| 502/503/504 | `UPSTREAM_FAILURE`, `DEPENDENCY_UNAVAILABLE`, `UPSTREAM_TIMEOUT` | Dependency failure; no raw upstream body. |
| 500 | `INTERNAL_ERROR` | Unexpected fault with request ID for support. |

Centralized Spring exception handling and FastAPI exception handlers should produce the envelope. Validation `details` are sanitized field names/reasons only. Document all responses in OpenAPI and verify contract examples in tests.

## Citation contract

Answers and findings must identify `repositoryId`, `commitSha`, repository-relative `path`, `startLine`, and `endLine` (one-based, inclusive). Cite only content actually retrieved from that immutable snapshot. A missing or stale snapshot produces an explicit status, not fabricated line references.
