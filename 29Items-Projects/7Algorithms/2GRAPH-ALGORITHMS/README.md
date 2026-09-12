# Graph Algorithms

Local Java and Python implementations of common graph algorithms for learning and console demonstrations.

The application is a modular console monolith. There is no web frontend or backend API. The Python CLI also stores demo run history in SQLite using the `algorithm_runs` table from `migrations/001_algorithm_runs.sql`.

## Included Algorithms

- Dijkstra
- A*
- Floyd-Warshall
- Bellman-Ford
- Prim
- Kruskal
- Topological Sort
- Strongly Connected Components: Tarjan and Kosaraju
- Articulation Points
- Bridges
- Max Flow: Ford-Fulkerson and Edmonds-Karp
- Bipartite Matching

## Run Python Demo

```powershell
$env:PYTHONPATH="python"
python -m graph_algorithms.demo
```

## Run Python CLI

```powershell
$env:PYTHONPATH="python"
python -m graph_algorithms.cli list
python -m graph_algorithms.cli run --algorithm dijkstra
python -m graph_algorithms.cli run-all
python -m graph_algorithms.cli history
python -m graph_algorithms.cli benchmark
```

By default the Python CLI stores run history in the OS temp directory. Use `--db data/algorithm_runs.db` or set `ALGORITHM_RUNS_DB` when you want a project-local SQLite file.

JSON input is supported for `dijkstra`, `astar`, `bellman-ford`, and `floyd-warshall`:

```json
{
  "vertices": ["A", "B", "C"],
  "edges": [["A", "B", 3], ["B", "C", 1]],
  "start": "A",
  "goal": "C"
}
```

```powershell
python -m graph_algorithms.cli run --algorithm dijkstra --input sample.json
```

## Run Python Tests

```powershell
pip install -r requirements-dev.txt
pytest
```

## Run Java Demo

```powershell
cd java
mvn -q test
java -cp target/classes com.example.graphalgorithms.App run-all
```

Other Java commands:

```powershell
java -cp target/classes com.example.graphalgorithms.App list
java -cp target/classes com.example.graphalgorithms.App run dijkstra
java -cp target/classes com.example.graphalgorithms.App benchmark
```

## Run Java Tests

```powershell
cd java
mvn test
```

## Database

The Python CLI creates the SQLite table automatically. To inspect or apply the migration manually, use:

```powershell
python -m graph_algorithms.cli run --algorithm dijkstra --db data/algorithm_runs.db
sqlite3 data/algorithm_runs.db "select * from algorithm_runs;"
```

## Local Checks

Run these before committing:

- `ruff check --no-cache python`
- `pytest --cov=graph_algorithms --cov-fail-under=70`
- `mvn test`
- `mvn package`

## Troubleshooting

- If Python imports fail, set `PYTHONPATH` to `python` before running modules.
- If `java -cp target/classes ...` fails, run `mvn test` first so Maven compiles the classes.
- If SQLite cannot write to `data/` on Windows, omit `--db`; the CLI will use an OS temp directory.
- If Maven reports an unsupported release, use JDK 11 or newer.
