# Technical Notes

## 3.1 CI/CD Pipeline Design

The default pipeline is validation-only because this project runs locally.

Recommended stages:

1. Lint Python with Ruff.
2. Run Python tests with pytest.
3. Compile Java with Maven.
4. Run Java tests with JUnit.
5. Package Java classes only if a distributable demo is needed.

No deployment stage is required. If hosting is added later, publish generated documentation or a small static site separately from the algorithm source.

## 3.2 Testing Strategy

- Python: use `pytest`; target 85 percent coverage for algorithm modules.
- Java: use JUnit 5 through Maven Surefire.
- Unit tests should assert algorithm values and reconstruction outputs.
- Integration tests should execute the console runner and verify that expected section headings appear.
- End-to-end tests are not necessary until a UI or API is introduced.

Important edge cases:

- Empty input collections.
- Capacity or amount equal to zero.
- Duplicate values in LIS.
- No possible coin-change solution.
- One-character and empty strings.
- TSP with small complete graphs only.

## 3.3 Deployment Strategy

The target deployment model is local execution:

- Python: install development dependencies and run `python -m dp_algorithms.demo`.
- Java: run `mvn test` or `mvn exec:java`.
- Scripts under `scripts/` wrap common commands for Windows PowerShell.

Containerization is intentionally omitted because the project does not need Docker.

## 3.4 Environment Management

Use `.env.example` as the source of truth for optional local settings. For now, environment values are limited to demo verbosity and optional persistence settings.

Example:

```text
DP_DEMO_VERBOSE=true
DP_STORE_RUNS=false
DP_DATABASE_URL=sqlite:///dp_runs.db
```

For future environments:

- Development: verbose console output and small sample inputs.
- Staging: optional persistence enabled against disposable data.
- Production: only relevant if this becomes a web/API service; enforce input limits and structured logs.

## 3.5 Version Control Workflow

Use GitHub Flow:

- Keep `main` releasable.
- Create short-lived branches for each algorithm group or documentation update.
- Require tests before merging.
- Prefer small pull requests so algorithm correctness is easy to review.

Gitflow is heavier than this project needs because there are no release trains or deployed environments.

## 3.6 Common Pitfalls

- Mixing subsequence and substring problems. LCS and longest palindromic substring have different state transitions.
- Using space optimization where reconstruction is required. Rolling arrays often lose the parent pointers needed for backtracking.
- Letting demo inputs grow too large. TSP, Egg Drop, and Optimal BST can become slow or memory-heavy.
- Comparing Java and Python outputs only by text. Tests should validate values directly.
- Treating memoization as automatically faster. Recursive overhead can matter for small inputs.
- Forgetting deterministic ordering when multiple optimal answers exist.
