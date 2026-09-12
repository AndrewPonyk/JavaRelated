# Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended pipeline stages:

1. Lint: run `ruff check python` and Maven compiler checks.
2. Test: run `pytest` and `mvn test`.
3. Build: package Python metadata and Java jar.
4. Deploy: not needed for local execution. Keep this stage as a no-op until a real target exists.

For GitHub Actions, use a matrix only if the project needs multiple Java or Python versions. For now, one supported Java LTS and one Python version keeps feedback fast.

## 3.2 Testing Strategy

Unit testing:

- Python: `pytest`, with target coverage of 80% for algorithm modules.
- Java: JUnit 5, with tests for normal examples, empty input, invalid input, and tie handling.

Integration testing:

- Run both CLIs and assert that each exits successfully.
- Use sample fixtures from `data/examples.json`.
- Keep output assertions focused on stable facts, not every line of teaching copy.

End-to-end testing:

- For this CLI project, E2E means executing `scripts/run-python.ps1` and `scripts/run-java.ps1`.
- If the optional API becomes active, add HTTP tests using FastAPI `TestClient`.

## 3.3 Deployment Strategy

Deployment is currently local execution only.

Containerization strategy:

- Use `Dockerfile` for reproducible local demo runs.
- Use `docker-compose.yml` only when enabling optional services like SQLite browser, docs server, or API mode.
- Do not introduce Kubernetes or cloud deployment until the project has a real multi-user requirement.

## 3.4 Environment Management

Use `.env.example` as the contract for configuration. Developers can copy it to `.env` locally.

Expected groups:

- Runtime mode: `GREEDY_ENV`, `GREEDY_LOG_LEVEL`.
- Storage: `GREEDY_DATABASE_URL`.
- API: `GREEDY_API_HOST`, `GREEDY_API_PORT`.

Avoid environment-specific config files checked into source control. Use defaults for local CLI execution.

## 3.5 Version Control Workflow

Use **trunk-based development with short-lived branches**.

Rationale:

- The project is small and educational.
- Small branches keep algorithm changes easy to review.
- CI remains the main quality gate.
- Gitflow would add ceremony without improving release safety for this scope.

Recommended branch names:

- `feature/activity-selection-demo`
- `fix/huffman-empty-input`
- `docs/proof-notes`

## 3.6 Common Pitfalls

- Confusing greedy interval scheduling with weighted interval scheduling. Weighted interval scheduling generally needs DP.
- Treating fractional knapsack as equivalent to 0/1 knapsack. Fractional is greedy-solvable; 0/1 is not generally greedy-solvable.
- Forgetting deterministic tie-breaking, which makes teaching output inconsistent.
- Mixing presentation code with algorithm logic, which makes tests brittle.
- Overbuilding web infrastructure for a local teaching tool.
- Letting Java and Python examples drift semantically. Shared sample fixtures help keep behavior comparable.
