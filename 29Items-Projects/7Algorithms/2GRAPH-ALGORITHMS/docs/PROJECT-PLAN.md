# Graph Algorithms Project Plan

## 1.1 Project File Structure

This project is a local learning-oriented graph algorithm suite. It intentionally uses a simple layered layout instead of a web frontend or backend API. The Python CLI records algorithm runs in SQLite.

```text
2GRAPH-ALGORITHMS/
  docs/
    PROJECT-PLAN.md
    ARCHITECTURE.md
    TECH-NOTES.md
  python/
    graph_algorithms/
      graph.py
      dijkstra.py
      astar.py
      floyd_warshall.py
      bellman_ford.py
      prim.py
      kruskal.py
      topological_sort.py
      tarjan_scc.py
      kosaraju_scc.py
      articulation_points.py
      bridges.py
      ford_fulkerson.py
      edmonds_karp.py
      bipartite_matching.py
      benchmark.py
      cli.py
      demo.py
      repository.py
      samples.py
    tests/
      test_basic_algorithms.py
  java/
    pom.xml
    src/main/java/com/example/graphalgorithms/
      Dijkstra.java
      AStar.java
      FloydWarshall.java
      BellmanFord.java
      Prim.java
      Kruskal.java
      TopologicalSort.java
      TarjanScc.java
      KosarajuScc.java
      ArticulationPoints.java
      Bridges.java
      FordFulkerson.java
      EdmondsKarp.java
      BipartiteMatching.java
      App.java
    src/test/java/com/example/graphalgorithms/
      BasicAlgorithmsTest.java
      AllAlgorithmsTest.java
  migrations/
    001_algorithm_runs.sql
  config/
    logging.properties
  .env.example
  .gitignore
  pyproject.toml
  requirements-dev.txt
  README.md
```

### Source Code

- Python algorithms live in `python/graph_algorithms`, one algorithm per file.
- Java algorithms live in `java/src/main/java/com/example/graphalgorithms`, one algorithm per file.
- `demo.py`, `cli.py`, and `App.java` provide console demonstration entry points.
- `graph.py` contains a small shared Python graph helper. Java examples stay self-contained to keep each algorithm easy to read independently.
- `repository.py` implements SQLite CRUD for algorithm run history.

### Frontend, Backend, Shared Modules, and Database

- Frontend: not applicable. This is a console learning app.
- Backend API: not applicable. Algorithm files are callable library-style modules and console commands.
- Shared modules: minimal shared Python graph helpers; Java examples are mostly standalone.
- Database: SQLite run history through `algorithm_runs`.

### Local Execution

- Python commands run demos and tests.
- Java/Maven commands run demos and tests.

### Tools Configuration

- Python tooling: `pyproject.toml`, `requirements-dev.txt`
- Java tooling: `java/pom.xml`
- Environment template: `.env.example`
- Logging config: `config/logging.properties`

## 1.2 Implementation Checklist

### Phase 1: Foundation (High Priority)

- [x] Keep one readable file per algorithm in both Java and Python.
- [x] Add simple graph examples for directed, undirected, weighted, and matrix-based inputs.
- [x] Provide console output for every algorithm.
- [x] Add basic unit tests for representative shortest path, traversal, MST, SCC, and flow logic.
- [x] Keep setup instructions short and runnable on a local machine.

### Phase 2: Core Features (Medium Priority)

- [x] Add richer examples for negative weights and disconnected graphs.
- [x] Add input validation for malformed graphs.
- [x] Add CLI selection for running one algorithm at a time.
- [x] Add more unit tests around edge cases.
- [x] Normalize console output formatting across Java and Python.

### Phase 3: Polish & Optimization (Lower Priority)

- [x] Add complexity notes through docs and readable implementation names.
- [x] Add benchmark examples for representative graph runs.
- [x] Add JSON input support for Python CLI shortest-path algorithms.
- [x] Add documentation pages with diagrams for the application flow.
- [x] Add edge-case tests for selected algorithms.
