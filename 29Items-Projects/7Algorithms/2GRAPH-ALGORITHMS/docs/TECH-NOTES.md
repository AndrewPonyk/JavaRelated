# Technical Notes

## 3.1 Local Verification

Recommended local verification flow:

1. Lint and formatting checks.
2. Python unit tests with `pytest`.
3. Java unit tests with Maven and JUnit.
4. Build/package stage.

Use the same commands locally before committing changes.

## 3.2 Testing Strategy

- Python: use `pytest`.
- Java: use JUnit 5 through Maven.
- Coverage target: Python coverage is enforced at 70% or higher in pytest.
- Unit tests should use small graphs with known results.
- Integration tests cover the Python CLI and SQLite repository flow.
- End-to-end tests are not needed until there is a UI or API.

## 3.3 Execution Strategy

The project runs locally through Python commands and Java/Maven commands.

## 3.4 Environment Management

Use `.env.example` as documentation for local settings. Keep real `.env` files out of version control.

Example variables:

```text
APP_ENV=local
LOG_LEVEL=INFO
PYTHONPATH=python
ALGORITHM_RUNS_DB=data/algorithm_runs.db
```

## 3.5 Version Control Workflow

Use GitHub Flow:

- `main` stays runnable.
- Create short-lived feature branches.
- Open pull requests with test output.
- Merge after review and passing local tests.

This is simpler than Gitflow and fits a small local learning project.

## 3.6 Common Pitfalls

- Mixing directed and undirected graph assumptions in the same fixture.
- Using Dijkstra with negative edges.
- Forgetting that Floyd-Warshall expects matrix-style all-pairs input.
- Treating DFS discovery order as deterministic when maps are unordered.
- Confusing Ford-Fulkerson as a method family with Edmonds-Karp as the BFS-based variant.
- Adding too much framework code before the algorithms are clear and tested.
