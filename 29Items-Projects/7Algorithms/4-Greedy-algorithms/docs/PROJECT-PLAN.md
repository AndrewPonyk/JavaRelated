# GREEDY Project Plan

## 1.1 Project File Structure

This project is a local-first educational monolith with two runtime tracks:

- Java CLI demonstrations for strongly typed algorithm implementations.
- Python CLI and optional API stubs for fast experimentation and teaching.

```text
.
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- config/
|   `-- logging.properties
|-- data/
|   `-- examples.json
|-- docs/
|   |-- ARCHITECTURE.md
|   |-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
|-- java/
|   |-- pom.xml
|   `-- src/
|       |-- main/java/com/example/greedy/
|       |   |-- Main.java
|       |   |-- algorithms/
|       |   |-- console/
|       |   `-- model/
|       `-- test/java/com/example/greedy/algorithms/
|-- migrations/
|   `-- 001_initial_schema.sql
|-- python/
|   |-- greedy_algorithms/
|   |   |-- __init__.py
|   |   |-- __main__.py
|   |   |-- api.py
|   |   |-- cli.py
|   |   |-- console_view.py
|   |   `-- algorithms/
|   `-- tests/
|-- scripts/
|   |-- run-java.ps1
|   `-- run-python.ps1
|-- .editorconfig
|-- .env.example
|-- .gitignore
|-- Dockerfile
|-- docker-compose.yml
|-- pyproject.toml
`-- README.md
```

### Source Code

- `java/src/main/java/com/example/greedy/algorithms`: Java implementations for Activity Selection, Huffman Coding, Fractional Knapsack, Job Scheduling, MST, and Interval Scheduling.
- `python/greedy_algorithms/algorithms`: Python implementations of the same algorithms.
- `java/src/main/java/com/example/greedy/console` and `python/greedy_algorithms/console_view.py`: Presentation layer for readable console output.
- `python/greedy_algorithms/api.py`: Optional backend-style REST stub for CRUD over demo scenarios.
- `migrations/`: SQL schema for persisting algorithm runs and scenarios if this learning project later adds storage.
- `data/`: Reusable sample inputs.

### CI/CD

- `.github/workflows/ci.yml`: Runs Python lint/tests and Java Maven tests.
- Future alternatives can be added under `ci/jenkins/`, `ci/gitlab/`, or `infrastructure/` if the deployment target changes.

### Tools Configuration

- `pyproject.toml`: Python package, pytest, ruff, and formatting configuration.
- `java/pom.xml`: Maven build, JUnit test setup, and Java version.
- `.editorconfig`: Cross-editor whitespace defaults.
- `.env.example`: Documented runtime configuration template.
- `Dockerfile` and `docker-compose.yml`: Optional local container execution.
- `config/logging.properties`: Java logging placeholder.

## 1.2 Implementation TODO List

### Phase 1: Foundation - High Priority

- [x] Create repository structure for Java and Python tracks.
- [x] Add architecture and technical documentation.
- [x] Add runnable CLI entry points for both languages.
- [x] Add representative algorithm implementations in Java and Python.
- [x] Add sample input data and environment template.
- [x] Add initial CI workflow stub.
- [ ] Expand unit tests for every algorithm and edge case.
- [ ] Add README examples with expected console output snapshots.

### Phase 2: Core Features - Medium Priority

- [ ] Add interactive menu mode for choosing one algorithm at a time.
- [ ] Add DP comparison modules where applicable:
  - [ ] Weighted interval scheduling versus greedy interval scheduling.
  - [ ] 0/1 knapsack dynamic programming versus fractional knapsack greedy.
  - [ ] Activity selection proof and counterexample discussion for wrong greedy choices.
- [ ] Add graph input parser for MST examples.
- [ ] Add persisted run history using the migration schema.
- [ ] Add optional REST API implementation around saved scenarios.
- [ ] Add richer proof notes in console output.

### Phase 3: Polish & Optimization - Lower Priority

- [ ] Add benchmark mode for larger generated inputs.
- [ ] Add property-based tests for selected algorithms.
- [ ] Add Markdown report generation for algorithm runs.
- [ ] Add Docker image publishing if the project moves beyond local execution.
- [ ] Add diagrams that compare greedy, DP, and brute force decision trees.
- [ ] Add localized examples and teaching exercises.
