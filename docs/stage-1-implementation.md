# Stage 1 closure verification

**Status: PARTIAL pending a live GitHub App end-to-end installation and a frontend callback implementation.** The Stage 1 backend handoff, isolated PostgreSQL tests, and mocked GitHub tests are implemented and pass. No Stage 2 functionality is included.

## Implemented modules

- `auth`: registration, login, signed access JWTs, hashed and rotating refresh tokens, logout.
- `user`: local account persistence and safe profile.
- `project`: project CRUD, soft delete, membership and OWNER/ADMIN/MEMBER rules.
- `github`: App installation handoff using installation URL state, setup ID binding, explicit user OAuth, GitHub verification, project association, connection status, and repository metadata. No webhook or cloning.
- `common`: security filter chain, CORS, request ID, centralized API errors.
- Flyway V1 defines Stage 1 tables; V2 makes the intended installation ID nullable until setup handoff.

## Verification on 2026-11-05

| Check | Result |
| --- | --- |
| Host Maven compilation and package | PASS; Java 26 running a Java 21 release build. |
| Full host JUnit suite | PASS; 31 discovered, 28 executed, 0 failed, 3 conditionally skipped `DatabaseLiveIntegrationTest` methods because `DATABASE_URL` was unset for this run. |
| Previously skipped `DatabaseIntegrationTest` methods | PASS; 2 executed against a Testcontainers PostgreSQL 18 container with database `test`. Docker Desktop's native `npipe:////./pipe/docker_engine` was discovered. No host socket was mounted into a container. |
| Testcontainers cleanup | PASS; the container started and no Testcontainers-labelled containers remained after the run. |
| Live PostgreSQL integration tests | PASS; all 3 conditionally skipped methods were executed in a separate run against a disposable Compose database and rolled back their rows. Across these two runs, every one of the 31 JUnit methods executed and passed. |
| Flyway V1 and V2 | PASS; both applied on the isolated Compose database and the fresh Testcontainers database. Hibernate schema validation passed. |
| GitHub mocked HTTP/client and service tests | PASS; 3 HTTP client tests and 13 service tests in the full suite. No live GitHub credentials used. |
| Compose configuration | PASS with `.env.example` and disposable private environment. |
| Isolated backend Compose startup and health | PASS; backend, PostgreSQL, and Redis healthy; readiness returned `UP`, Redis returned `PONG`. |
| Isolated authentication/project HTTP smoke | PASS; 43 checks including JWT rejection, refresh rotation and revocation, concurrent refresh, OWNER/ADMIN/MEMBER authorization, cross-project isolation, CORS, and health. |
| Live GitHub App installation and frontend redirect/callback | NOT RUN; no real App credentials or frontend routes supplied. |

The first host Testcontainers attempt failed after the PostgreSQL container started because PostgreSQL 18 rejected the host JVM timezone alias `Asia/Calcutta`. The Maven Surefire test JVM now uses UTC, and the rerun passed. The original Docker socket mount request was rejected by automatic review; the host native pipe path resolved the two skipped tests without changing Docker security settings.

The disposable database contained exactly three run-identified smoke users and one soft-deleted smoke project after the HTTP test. It had no unrelated records. The smoke test requires `--disposable-database` because the public API cannot delete those users. The existing development Compose project and database were not changed.

## GitHub handoff

The OWNER/ADMIN starts with no installation ID. The server returns GitHub's App installation URL with a random state bound to actor and project. After GitHub redirects to the configured setup URL, the authenticated frontend forwards `state` and `installation_id` to the setup API. The backend binds that ID once and returns an explicit OAuth authorization URL. The frontend forwards OAuth `code` and `state` to the completion API. Completion rechecks the initiating actor's project role, exchanges the code, verifies user installation access and App installation identity through GitHub, then links the installation only to the initiating project and consumes state. Cancel also consumes state; abandoned state expires after ten minutes. See [frontend handoff contract](github-handoff.md) and [security design](security.md).

## Limits and next gate

- No live GitHub App installation was performed. The backend is verified with mocked GitHub HTTP and service tests; credentials and real frontend routes are needed for end-to-end validation.
- The frontend is documentation only in Stage 1; `/github/setup` and `/github/callback` are not implemented pages.
- Auth rate limiting, email verification, password reset, access-token revocation, production TLS/key management, and audit history remain production hardening work.
- No repository clone, webhook processing, scan, AI analysis, or background job behavior exists in Stage 1.

Do not begin Stage 2 until the remaining live integration gate has been reviewed and approved.
