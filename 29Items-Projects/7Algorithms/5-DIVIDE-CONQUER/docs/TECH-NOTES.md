# Technical Notes

## 1. CI/CD Pipeline Design

The pipeline is a quality gate, not a deployment pipeline.

Stages:

1. Checkout source.
2. Set up Java 11 or newer.
3. Run `mvn test`.
4. Set up Python.
5. Install `requirements-dev.txt`.
6. Run `ruff check --no-cache src/main/python src/test/python`.
7. Run `pytest`.

There is no deploy stage because this project runs locally from source.

## 2. Testing Strategy

### Java

- Framework: JUnit 5.
- Coverage style: algorithm smoke tests, deterministic randomized selection tests, baseline matrix comparison, invalid input tests, and geometry edge cases.
- Command: `mvn test`.

### Python

- Framework: pytest.
- Coverage style: algorithm smoke tests, deterministic randomized selection tests, baseline matrix comparison, invalid input tests, and geometry edge cases.
- Command: `pytest` for tests only, or `.\scripts\test_all.ps1` for Java tests plus Python coverage.

## 3. Deployment Strategy

Deployment is not applicable. The project is intended for local execution by students.

## 4. Environment Management

The project does not require secrets or environment-specific service settings.

`.env.example` is intentionally minimal and documents optional local knobs:

```env
DEMO_INPUT_SIZE=8
DEMO_PRINT_COMPLEXITY=true
```

CLI flags are preferred for algorithm selection and benchmark sizes because they are explicit at execution time.

## 5. Version Control Workflow

Use GitHub Flow:

- Keep `main` releasable.
- Create short-lived feature branches.
- Open pull requests with a focused algorithm or documentation change.
- Require tests before merge.

This is simpler than Gitflow and fits a small educational repository.

## 6. Common Pitfalls

- Strassen requires square matrices; non-power-of-two sizes are padded internally before recursion.
- FFT requires input length to be a power of two.
- Quickselect should copy caller input before partitioning.
- Median of Medians is more verbose than randomized selection, but it gives deterministic worst-case linear time.
- Closest pair implementations often become incorrect when the strip comparison is not bounded by sorted-by-y order.
- Convex hull behavior must be explicit for duplicate and collinear points.
- Java and Python demos should use the same sample inputs so students can compare outputs directly.
- Keep every added algorithm available in both Java and Python, with matching CLI names and tests.
