# Product requirements (Stage 0 baseline)

## Purpose and users

CodePilotgit helps an authenticated developer or team understand and improve GitHub-hosted code. A user connects a GitHub App installation, selects repositories, requests analysis, and sees evidence linked to a specific commit and file location. Project membership determines access to all project data.

## Functional requirements

| ID | Capability | Acceptance direction |
| --- | --- | --- |
| FR-01 | Identity and project access | Authenticate users; enforce project membership and roles on every resource and job. |
| FR-02 | GitHub connection | Install a GitHub App with minimum permissions; synchronize installation and repository metadata from signed webhooks and API reconciliation. |
| FR-03 | Repository ingestion | Index a selected immutable commit, track status, and exclude unsupported or oversized content under documented limits. |
| FR-04 | Code questions | Retrieve authorized chunks and answer with repository, commit SHA, path, and line-range citations. Indicate when evidence is insufficient. |
| FR-05 | Pull request review | Analyze a pinned PR head/base, produce findings with severity and evidence, and re-run when the head changes. Human review remains authoritative. |
| FR-06 | Static analysis | Run Semgrep and selected language linters in a restricted worker environment; store normalized findings. |
| FR-07 | Test generation | Generate proposed test changes for review; never execute generated or repository code on application hosts. |
| FR-08 | Quality dashboard | Show scoped trends, analysis state, and provenance for metrics; avoid implying an unrun scan is clean. |
| FR-09 | Auditability | Record actor, project, action, target, outcome, and time without logging secrets or source content. |

## Quality and security requirements

- Use PostgreSQL as the source of truth; jobs must survive process restarts and be idempotent where external events can repeat.
- Keep Redis for coordination and cache data only. Database job state is authoritative.
- Every stored project-owned row carries a project identifier or an unambiguous foreign-key path to one; queries must enforce tenant scope. Add authorization tests for cross-project access.
- Encrypt sensitive integration material at rest using a managed key in production. Store GitHub App private keys and provider keys outside the database where possible; never expose them to the browser.
- Verify GitHub webhook signatures, reject replay where feasible, use short-lived installation tokens, and request least-privilege scopes.
- Treat repository content, comments, and AI responses as untrusted. Defend against prompt injection, unsafe output rendering, path traversal, SSRF, oversized inputs, and leaking one project's code into another project's retrieval.
- Analyze untrusted repository code only in isolated, resource-limited containers or workers with no host mounts, no privileged mode, and restricted network access. Stage 0 does not supply such a runner.
- Provide health/readiness checks when services are implemented, structured logs with correlation IDs, redaction, and metrics. Do not log repository contents or credentials.
- Validate request size and shape, limit concurrency and cost, use timeouts/retries for external calls, and define retention/deletion behavior before indexing user code.
- Test domain behavior, access control, API contracts, ingestion boundaries, and failure handling. Run tests in CI before release.

## Non-goals for Stage 0

No working application endpoints, migrations, GitHub App registration, AI provider integration, code execution, or dashboard are delivered in this stage. Stage 0 delivers contracts and local database/cache infrastructure only.

## Open product decisions

- Initial authentication provider and team invitation flow.
- AI provider, model, data residency, and code retention policy.
- Supported languages and maximum repository/PR size for the first release.
- Whether generated tests will be offered as downloadable patches or proposed pull requests.

## Next stage

Build a tested vertical slice: Spring Boot application foundation, migrations and project authorization; GitHub App installation/webhook validation; repository metadata sync; health checks; and a minimal React flow to show connection state. Add a small FastAPI health-ready service only when the backend needs its first real internal contract.
