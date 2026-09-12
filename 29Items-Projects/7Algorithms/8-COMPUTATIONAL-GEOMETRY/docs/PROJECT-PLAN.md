# Project Plan

## Project Summary

**Project Name:** Computational Geometry Algorithms

**Purpose:** A local, console-first learning project that demonstrates core 2D computational geometry algorithms in both Java and Python. The implementation emphasizes readable code, numerical stability, deterministic examples, and simple visualization experiments.

**Scope:** No frontend, backend server, database, or deployment infrastructure is required. Generic application layers are intentionally represented as "not applicable" because this project is a local algorithm library plus command-line runners. Docker Compose is provided only as an optional reproducible runner for the local checks.

## 1.1 Project File Structure

```text
.
├── .github/
│   └── workflows/
│       └── ci.yml                         # CI pipeline for Java and Python checks
├── data/
│   └── fixtures/                          # CSV/JSON demo inputs
├── config/
│   └── algorithms.example.yml             # Example algorithm/runtime configuration
├── docs/
│   ├── ARCHITECTURE.md                    # Architecture description and diagrams
│   ├── PROJECT-PLAN.md                    # Project plan and implementation checklist
│   └── TECH-NOTES.md                      # CI, testing, deployment, and workflow notes
├── experiments/
│   └── visualization/
│       └── matplotlib_demo.py             # Optional local visualization experiment
├── src/
│   ├── java/
│   │   ├── pom.xml                        # Java build, test, and style configuration
│   │   └── src/
│   │       ├── main/java/org/computationalgeometry/
│   │       │   ├── algorithms/            # Java algorithm implementations
│   │       │   ├── cli/                   # Java console entry point
│   │       │   ├── model/                 # Geometry domain types
│   │       │   └── util/                  # Numeric predicates and formatting helpers
│   │       └── test/java/org/computationalgeometry/
│   │           └── GeometrySmokeTest.java # Java smoke tests
│   └── python/
│       └── geometry/
│           ├── algorithms/                # Python algorithm implementations
│           ├── cli/                       # Python console entry point
│           ├── model/                     # Geometry domain types
│           └── util/                      # Numeric predicates and formatting helpers
├── tests/
│   └── python/
│       └── test_smoke.py                  # Python smoke tests
├── tools/
│   └── run_all.ps1                        # Local convenience runner
├── docker-compose.yml                     # Optional Java/Python console verification runner
├── .dockerignore                          # Docker build context hygiene
├── .editorconfig                          # Cross-editor formatting defaults
├── .env.example                           # Local runtime settings template
├── .gitignore                             # Ignore generated files and local envs
├── pyproject.toml                         # Python project, linting, and test config
├── README.md                              # Quick start and algorithm catalog
└── GPT-5.txt                              # Empty model marker requested by prompt
```

### Source Code Organization

- **Java source:** `src/java/src/main/java/org/computationalgeometry`.
- **Python source:** `src/python/geometry`.
- **Shared design concepts:** Both languages use the same conceptual layers:
  - `model`: immutable 2D geometry primitives.
  - `util`: numeric predicates, epsilon handling, and console formatting.
  - `algorithms`: one file/class per computational geometry algorithm.
  - `cli`: a deterministic demo runner with readable console output.
- **Frontend:** Not applicable. The product requirement is console output only.
- **Backend/API:** Not applicable. No HTTP server is needed for local algorithm demos.
- **Database/migrations:** Not applicable. Inputs are in-memory fixtures or local CSV/JSON files.
- **Configurations:** `.env.example`, `config/algorithms.example.yml`, `pyproject.toml`, `pom.xml`, `.editorconfig`.

### CI/CD Structure

- **GitHub Actions:** `.github/workflows/ci.yml`.
- **Java pipeline:** compile, unit tests, package.
- **Python pipeline:** install package, lint-ready checks, unit tests.
- **Deployment:** Not applicable. This project is local execution only.
- **Release artifacts:** Optional future JAR and Python wheel distribution.

### Tools Configuration

- **Java:** Maven with JUnit 5.
- **Python:** `pyproject.toml` configured for pytest, Ruff, Black-compatible formatting line length.
- **Editor defaults:** `.editorconfig`.
- **Runtime examples:** `.env.example` and `config/algorithms.example.yml`.
- **Local runner:** `tools/run_all.ps1`.

## 1.2 Implementation Checklist

### Phase 1: Foundation (High Priority)

- [x] Finalize common geometry vocabulary for both languages: `Point2D`, `Segment2D`, `Polygon2D`.
- [x] Implement robust orientation, signed area, distance, and epsilon comparison helpers.
- [x] Build deterministic demo fixtures for normal, degenerate, duplicate, and collinear inputs.
- [x] Implement Graham Scan in Java and Python.
- [x] Implement Point in Polygon in Java and Python.
- [x] Add smoke tests for both languages.
- [x] Document CLI usage in `README.md`.

### Phase 2: Core Features (Medium Priority)

- [x] Implement Closest Pair using divide-and-conquer in Java and Python.
- [x] Implement line segment sweep for intersection detection.
- [x] Implement Delaunay triangulation using a clear learning-friendly Bowyer-Watson approach.
- [x] Implement Voronoi diagram derivation from Delaunay triangulation.
- [x] Add edge-case tests for duplicate points, nearly collinear triples, vertical segments, and boundary points.
- [x] Add JSON/CSV input support for reusable examples.

### Phase 3: Polish & Optimization (Lower Priority)

- [x] Add microbenchmarks for large point clouds.
- [x] Improve numeric robustness with centralized documented tolerance modes.
- [x] Add Matplotlib visualizations for Python.
- [x] Add golden-output-style CLI tests for demo commands.
- [x] Package Java as a Maven-built console application.
- [x] Package Python as a console script.
- [x] Expand documentation with algorithm complexity tables and known limitations.
