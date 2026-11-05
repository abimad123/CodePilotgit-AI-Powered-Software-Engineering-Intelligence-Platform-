# Technology choices and version baseline

Checked against official release and compatibility documentation on **2026-11-04**. Stage 1 adds a Maven manifest using Spring Boot 4.1.2 dependency management, Spring Web MVC, Security, OAuth2 resource server, JPA, Validation, Actuator, Flyway, PostgreSQL, and Spring Boot Test. Testcontainers uses its 2.x artifact names (`testcontainers-postgresql`, `testcontainers-junit-jupiter`) per [official documentation](https://java.testcontainers.org/modules/databases/postgres/). Dependency resolution and packaging passed in the Docker Maven image; Hibernate ORM resolved to 7.4.13.Final and Testcontainers to 2.0.5.

| Component | Selected baseline | Reason / compatibility |
| --- | --- | --- |
| Java | 21 LTS | Required language baseline; supported by Spring Boot 4.1.x. |
| Spring Boot | 4.1.2 | Current stable line; its [system requirements](https://docs.spring.io/spring-boot/system-requirements.html) include Java 21. Spring manages compatible Spring, JUnit, and starter versions through its BOM. |
| Python | 3.12+ | Required AI-service baseline. Use a pinned 3.12 patch in the future runtime image. |
| FastAPI | 0.159.1 | Stable release listed in [official release notes](https://fastapi.tiangolo.com/release-notes/). Pin and resolve its Pydantic/Starlette graph when creating the Python lockfile. |
| React | 19.3 | Latest stable version per [React versions](https://react.dev/versions). |
| TypeScript | 5.x or newer, exact version deferred | Select alongside Vite and React type packages during frontend bootstrap; no unverified exact pin in Stage 0. |
| Tailwind CSS | 4.3 line | Stable major/minor announced by [Tailwind](https://tailwindcss.com/blog). Select exact patch with frontend lockfile. |
| Node.js | 24 LTS | [Supported LTS line](https://nodejs.org/en/about/previous-releases) for frontend tooling. |
| PostgreSQL | 18 | [Supported major](https://www.postgresql.org/support/versioning/) and current primary database. Compose uses pgvector's `0.8.7-pg18-trixie` image. |
| pgvector | 0.8.7 | [Official image tag and extension release](https://github.com/pgvector/pgvector#docker). One database keeps vectors transactional with project data. |
| Redis | 8.2 line | Extended-support [Redis release line](https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/). Compose uses `redis:8.2-bookworm`; patch moves within that line. |
| Docker Compose | v2 | Local infrastructure only, with bound localhost ports and health checks. |

## Planned dependencies, not installed in Stage 0

- Spring Web, Validation, Security, Data JPA, Actuator, PostgreSQL driver, Flyway, and Spring Boot Test/JUnit 5, managed by the selected Spring Boot BOM. Confirm starter names and transitive versions when creating `pom.xml`.
- FastAPI, an ASGI server, Pydantic, PostgreSQL driver, pgvector client, provider SDK, and Pytest. Choose exact compatible versions in a lockfile when the service gets real behavior.
- Vite, React DOM, Tailwind's Vite integration, React types, TypeScript, and Playwright. Choose exact versions in `package-lock.json` during frontend implementation.
- Semgrep and language-specific linters. Pin their versions and rule sets in the isolated scanner image when the first supported language and sandbox are chosen.

## Rationale

The Java monolith owns policy, durable workflows, and public contracts. Python keeps AI and parsing libraries out of the Java release cycle. PostgreSQL with pgvector avoids a separate vector database at initial scale. Redis is useful for cache and worker signaling but is not a durable queue of record. Docker Compose supplies repeatable local dependencies without pretending that unfinished services run.

Recheck these versions and security updates before future releases. Maven dependency resolution and the Stage 1 Docker build passed; frontend and AI package versions remain unpinned until those applications are implemented. This document is not a lockfile or a security update policy.
