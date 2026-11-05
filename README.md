# CodePilotgit

CodePilotgit is an AI-powered software engineering intelligence platform in development. Stage 1 adds a Java backend for local accounts, projects, memberships, and GitHub App repository metadata. AI, frontend, scans, and reviews are future work. The backend builds and runs in Docker; see [Stage 1 verification](docs/stage-1-implementation.md) for tested behavior and remaining limits.

## Repository layout

| Path | Purpose |
| --- | --- |
| `frontend/` | Future React and TypeScript application |
| `backend/` | Java 21 Spring Boot modular monolith source and tests |
| `ai-service/` | Future Python 3.12 FastAPI service |
| `infrastructure/` | Development database initialization and future deployment assets |
| `docs/` | Requirements, architecture, contracts, decisions, and setup |
| `scripts/` | Future repeatable development scripts |

Start with [requirements](docs/requirements.md), [architecture](docs/architecture.md), [technology choices](docs/technology.md), and [local setup](docs/local-development.md).

## Run the backend and local infrastructure

1. Install Docker Desktop with Compose v2.
2. Copy `.env.example` to `.env` and replace `POSTGRES_PASSWORD` and `JWT_SECRET_BASE64` with unique local secrets. Do not commit `.env`. An ignored `.env` with generated local secrets was created during verification in this workspace.
3. Run `docker compose config` to inspect the resolved configuration.
4. Run `docker compose up -d --build` to build the backend and start PostgreSQL, Redis, and the backend.
5. Run `docker compose ps` to check health; use `docker compose down` to stop containers.

See [local development](docs/local-development.md) for host execution, secret generation, migrations, and troubleshooting. The GitHub integration remains disabled until real App credentials are configured.

## Scope

The current backend source implements the Stage 1 scope described in [stage-1-implementation.md](docs/stage-1-implementation.md). Its verification status and known limitations are recorded there. Do not treat unexecuted tests or container startup as passed.
