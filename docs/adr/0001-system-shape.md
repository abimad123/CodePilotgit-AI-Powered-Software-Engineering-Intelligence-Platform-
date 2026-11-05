# ADR 0001: Modular monolith plus separate AI service

**Status:** Accepted for Stage 0 (2026-11-04)

## Context

CodePilotgit needs strong tenant authorization and transactional business workflows, while parsing, retrieval, and model integration have a distinct Python ecosystem and scaling profile.

## Decision

Build one Spring Boot backend with domain modules and one separately deployable FastAPI AI service. Use PostgreSQL as the business source of truth and vector store, Redis for ephemeral coordination/cache, and an isolated analysis worker for executing scanners. Keep one public API through Spring.

## Consequences

- Cross-module business transactions and authorization stay straightforward.
- AI dependencies and workload can evolve independently.
- The internal service contract and project-scoped data access need explicit authentication and tests.
- A worker is required before running Semgrep or language tools on untrusted code; it is not introduced as another public service.
- Revisit service separation only with measured scaling or ownership pressure.
