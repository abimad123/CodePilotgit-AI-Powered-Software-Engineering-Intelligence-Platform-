# Local development setup

Stage 1 adds a backend service to Compose. The build and startup commands below were verified with Docker Engine in this workspace. The default host port 5432 was unavailable here, so the ignored local `.env` uses PostgreSQL port 15432, Redis port 16379, and backend port 18080; Compose still uses service names and standard internal ports.

## Prerequisites

- Docker Desktop with Compose v2. For host execution: Java 21+ and Maven 3.6.3+. Python and Node are not needed for this backend stage.

## Start infrastructure

From the repository root in PowerShell:

```powershell
Copy-Item .env.example .env
# Edit .env: replace POSTGRES_PASSWORD and JWT_SECRET_BASE64 with unique secrets.
docker compose config
docker compose up -d --build
docker compose ps
```

Compose binds backend, PostgreSQL, and Redis to `127.0.0.1` only. Backend waits for a healthy database. Flyway runs `V1__foundation.sql` on startup, then Hibernate validates the schema. The PostgreSQL image includes pgvector; the Stage 0 init script enables it on first volume creation. Redis remains available for future work but is not used by Stage 1 backend code.

Generate a JWT secret in PowerShell, then put the resulting base64 string in your private `.env` file:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

For backend execution outside Docker, start PostgreSQL with `docker compose up -d postgres`, set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `JWT_SECRET_BASE64` in the shell, then run `mvn -f backend/pom.xml spring-boot:run`. The backend defaults its JDBC URL to `jdbc:postgresql://localhost:5432/codepilotgit`; set it explicitly if you changed the local port. GitHub credentials may be left empty.

To inspect services:

```powershell
docker compose logs backend postgres
docker compose exec backend curl --fail http://localhost:8080/actuator/health/readiness
docker compose exec postgres psql -U codepilotgit -d codepilotgit -c 'SELECT extversion FROM pg_extension WHERE extname = ''vector'';'
docker compose exec redis redis-cli ping
```

If you changed `POSTGRES_USER` or `POSTGRES_DB`, substitute those values in the `psql` command. The init script runs only when the volume is new. Flyway migrations are versioned under `backend/src/main/resources/db/migration`; do not edit a migration after it has been applied. Keep `.env` private. The `.env.example` password and JWT secret are placeholders and must be replaced before startup.

Run unit and PostgreSQL integration tests with `mvn -f backend/pom.xml test`. Testcontainers requires Docker Engine; it skips the PostgreSQL integration test when Docker is unavailable. Use `mvn -f backend/pom.xml package` to build the JAR. The GitHub App setup and token strategy are in [security](security.md).

When Maven is unavailable on the host but Docker Engine works, the build stage can run tests:

```powershell
docker build --target build -t codepilotgit-build ./backend
docker run --rm codepilotgit-build mvn -B -ntp test
python scripts/smoke-backend.py --base-url http://127.0.0.1:8080
```

The smoke script creates random development users and soft-deletes its project; it does not remove test users. To run the `DatabaseLiveIntegrationTest` against the existing Compose PostgreSQL service, supply `DATABASE_URL=jdbc:postgresql://postgres:5432/<database>`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `JWT_SECRET_BASE64` to the Maven test process and attach it to the `codepilotgit_default` network. Use a disposable development database and never commit those values.

```powershell
docker compose down
```

`docker compose down --volumes` destroys local database and cache data; use it only when you intentionally want a fresh development database.

## Troubleshooting

- If Compose reports a missing variable, create `.env` and fill the required PostgreSQL fields.
- If a local port is occupied, change `POSTGRES_PORT` or `REDIS_PORT` in `.env`.
- If Docker Engine is unavailable, the backend cannot connect to a local database unless a separate PostgreSQL 18 server is supplied.
- If backend startup rejects the JWT secret, ensure it is base64 for at least 32 random bytes.
- If migration or Hibernate validation fails, inspect the Flyway error before changing schema. Do not reset a volume that contains data you need.

## Verification status

Compose validation, Docker build/startup, Flyway V1 migration on an empty database, Hibernate schema validation, 19 executed JUnit tests, and 42 HTTP smoke checks passed in this workspace. Two Testcontainers tests were skipped because the Maven container had no Docker socket; host socket mounting was rejected by automatic approval review. See [Stage 1 implementation](stage-1-implementation.md).
