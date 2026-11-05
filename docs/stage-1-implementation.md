# Stage 1 implementation status

**Status: PARTIAL.** The backend compiled and packaged in Docker, started with PostgreSQL 18, applied Flyway V1 to an empty schema, passed Hibernate schema validation, and passed live API and database checks. The full Testcontainers test was skipped because the Maven container could not access Docker; automatic approval review rejected mounting the host Docker socket due to its broad daemon control. A safer live PostgreSQL integration test passed against the existing Compose service. The GitHub flow has mock HTTP coverage but no live App credentials or automated post-installation handoff.

## Modules and scope

- `auth`: registration, login, signed access JWT, hashed/rotating refresh tokens, logout.
- `user`: local account persistence and current-user profile.
- `project`: project creation, listing, update, soft delete, and existing-user membership with OWNER/ADMIN/MEMBER rules.
- `github`: OAuth state, App JWT, user authorization verification, installation association, repository metadata listing and connection. No webhook or cloning.
- `common`: security filter chain, CORS, request ID, centralized API errors.
- `V1__foundation.sql`: eight Stage 1 tables, constraints, indexes, and foreign keys.

## Environment variables

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Compose database initialization; password must be unique and private. |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Backend JDBC connection; Compose maps them from PostgreSQL values. |
| `JWT_SECRET_BASE64` | Required base64-encoded random signing key, at least 32 bytes. |
| `JWT_ISSUER`, `JWT_AUDIENCE`, `ACCESS_TOKEN_MINUTES`, `REFRESH_TOKEN_DAYS` | Token claims and lifetimes. |
| `CORS_ORIGINS` | Comma-separated browser origin allowlist. |
| `BACKEND_PORT`, `POSTGRES_PORT`, `REDIS_PORT` | Optional localhost port mappings. |
| `GITHUB_APP_ID`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_PRIVATE_KEY`, `GITHUB_REDIRECT_URI` | Optional GitHub App user authorization and installation API. All are needed to connect an App. |
| `GITHUB_API_BASE_URL`, `GITHUB_OAUTH_BASE_URL`, `GITHUB_CONNECT_TIMEOUT_MS`, `GITHUB_READ_TIMEOUT_MS` | Optional GitHub endpoint and timeout settings, useful for local mock testing. |
| `GITHUB_APP_NAME`, `GITHUB_WEBHOOK_SECRET`, `AI_PROVIDER_API_KEY` | Reserved for later flows; unused by Stage 1 backend. |

## Verification performed

| Check | Result |
| --- | --- |
| `docker compose --env-file .env.example config --quiet` | PASSED |
| Docker Maven build (`mvn -DskipTests package`) | PASSED; Java 21 compilation and JAR packaging |
| `docker compose up -d --build` | Initial start FAILED because host port 5432 was unavailable; PASSED after changing ignored local ports to 15432/16379/18080 |
| Flyway V1 on empty PostgreSQL 18.6 | PASSED; startup log reports one migration applied |
| Hibernate `ddl-auto=validate` and Actuator readiness | PASSED; application started and readiness returned HTTP 200 |
| `docker run --rm --network codepilotgit_default --env-file .env codepilotgit-build mvn -B -ntp test` | PASSED: 21 discovered, 19 passed, 2 skipped (Testcontainers) |
| `DatabaseLiveIntegrationTest` against Compose PostgreSQL | PASSED: 3 tests, no failures |
| `python scripts/smoke-backend.py --base-url http://127.0.0.1:18080` | PASSED: 42 checks, including auth, role isolation, concurrent refresh, CORS, and health |
| Full Testcontainers execution | BLOCKED by automatic approval review of host Docker socket mount |
| Live GitHub App connection | NOT RUN; no App credentials supplied |

## Reproduction commands

```powershell
docker compose --env-file .env.example config --quiet
cd backend
mvn -B -ntp test
mvn -B -ntp package
cd ..
docker compose up -d --build
docker compose ps
curl.exe -i http://localhost:8080/actuator/health/readiness
```

Set valid secrets in `.env` before startup. The Testcontainers test is available for environments where running it is approved. The live PostgreSQL integration test covers refresh, project persistence, uniqueness, and foreign-key constraints without Docker socket access.

## Known gaps before completion

- The Testcontainers database test remains unexecuted because the approved test environment does not expose a Docker socket. Automatic approval review rejected mounting it, citing broad Docker daemon control. User approval is required for that specific mount if it must be run in this workspace.
- A local mock HTTP test passed for GitHub JWT signing, OAuth token exchange, installation lookup, repository metadata, rate limits, and timeouts. No real App credentials were available. Authorization-error paths need further tests.
- No frontend callback page exists; the GitHub OAuth code/state must be forwarded manually for live development testing.
- Auth rate limiting, email verification, password reset, access-token revocation, production TLS/key management, and audit history are not implemented.
- No Stage 2 AI, scan, repository clone, PR review, webhook processing, or background jobs were started.

## Stage 2 dependency

First finish Stage 1 verification and fix any discovered failures. Only then add repository ingestion and analysis work behind the existing project boundary.
