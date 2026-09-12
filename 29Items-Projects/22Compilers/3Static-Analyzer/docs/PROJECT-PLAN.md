# Static Analyzer Project Plan

## Project Structure

```text
3Static-Analyzer/
  README.md
  docker-compose.yml
  docs/
    PROJECT-PLAN.md
    ARCHITECTURE.md
    TECH-NOTES.md
  backend/
    app/
      api/analysis_controller.py
      core/config.py
      db/entities.py
      db/session.py
      models/analysis.py
      services/analysis_service.py
      services/rule_service.py
      services/sarif_service.py
      services/source_analyzer.py
    tests/
  analyzer/libtooling/
  frontend/
  shared/schemas/
  migrations/
  config/
  docker/
  scripts/
  .github/workflows/
```

## Implemented Foundation

- API contracts exist for analysis jobs, findings, rule definitions, SARIF export, and health checks.
- Database integration is backed by SQL migrations and SQLAlchemy entities.
- Migrations are applied automatically during FastAPI startup.
- The analyzer emits stable JSON facts for functions, calls, variables, and control-flow markers.
- The backend can invoke the external analyzer binary when available and falls back to the same deterministic source scanner for local development and tests.
- CRUD endpoints are implemented for analysis jobs.
- Rule loading and validation are implemented from YAML rule packs.
- CI validates backend tests, frontend tests/build, C++ analyzer build, and Docker build.
- Docker Compose runs the backend and frontend stack with persistent SQLite storage.

## Implemented Core Features

- Data-flow style checks track local null assignments and tainted values into command execution sinks.
- Z3 verifies simple numeric branch feasibility.
- Initial vulnerability and quality rules cover unsafe calls, null dereference, tainted command execution, unreachable code, and path feasibility.
- Analysis results, findings, AST facts, evidence, and rule metadata are persisted.
- React supports job creation, result filtering, severity grouping, source-location display, rerun, deletion, and SARIF download.
- Integration tests cover source submission, file-based analysis, findings, SARIF, updates, deletion, rule loading, and failure states.
- IDE and CI integrations can consume the same REST and SARIF interfaces.

## Operational Capabilities

- Analysis is synchronous for predictable local execution.
- API services are stateless aside from database access and can be horizontally scaled with shared storage.
- Source size limits, request validation, and sanitized error responses are enforced.
- Logs and errors are represented through explicit service exceptions and HTTP status mapping.
