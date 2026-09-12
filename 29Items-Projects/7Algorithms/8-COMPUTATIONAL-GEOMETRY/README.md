# Computational Geometry Algorithms

Console-first Java and Python implementations of core 2D computational geometry algorithms with deterministic output, validation, tests, and optional visualization experiments.

## Implemented Algorithms

- Graham Scan convex hull
- Closest pair of points with divide-and-conquer
- Sweep-line-style segment intersection detection
- Point in polygon with `inside`, `outside`, and `boundary` results
- Delaunay triangulation with Bowyer-Watson
- Voronoi cells derived from Delaunay circumcenters

The project intentionally has no frontend, backend server, database, or API. Geometry primitives are in-memory entities and the application boundary is the console runner.

## Requirements

- Python 3.10+
- Java 11+
- Maven 3.8+
- Optional: Docker Compose

## Python Setup

```powershell
pip install -e ".[dev]"
geometry-demo --input data\fixtures\default_points.csv
pytest
```

Coverage check:

```powershell
$coverageFile = Join-Path $env:TEMP "geometry.coverage"
coverage run --data-file="$coverageFile" -m pytest
coverage report --data-file="$coverageFile" --fail-under=80 --show-missing
```

Without installing the console script:

```powershell
$env:PYTHONPATH = "src/python"
python -m geometry.cli.main --algorithm all --input data\fixtures\default_points.csv
```

## Java Setup

```powershell
cd src\java
mvn test
java -cp target\classes org.computationalgeometry.cli.Main --input ..\..\data\fixtures\default_points.csv
```

Executable JAR:

```powershell
cd src\java
mvn package
java -jar target\computational-geometry-0.1.0-SNAPSHOT.jar --input ..\..\data\fixtures\default_points.csv
```

## Run Everything Locally

```powershell
.\tools\run_all.ps1
```

## Docker Compose

Docker is only a convenience wrapper for the local console checks. It does not start a web server or database.

```powershell
docker compose up --build
```

## CLI Options

Both Java and Python support:

```text
--algorithm all|hull|closest|polygon|sweep|delaunay|voronoi
--input path/to/points.csv|points.json
```

CSV input must include `x,y` headers. JSON input must be an array of objects with `x` and `y` fields.

## Environment

The project does not require secrets. `.env.example` documents the only runtime limit:

```dotenv
GEOMETRY_MAX_INPUT_POINTS=1000000
```

## Visualization

```powershell
pip install -e ".[dev]"
$env:PYTHONPATH = "src/python"
python experiments\visualization\matplotlib_demo.py
```

## Architecture

The implementation follows the documented layered console monolith:

- `model`: immutable geometry primitives
- `util`: numeric predicates, formatting, and input loading
- `algorithms`: computational geometry implementations
- `cli`: console runners
- `experiments`: optional local visualization

## Troubleshooting

- If Docker reports `dockerDesktopLinuxEngine` pipe errors, start Docker Desktop and ensure Linux containers are enabled.
- If PowerShell cannot find `geometry-demo`, use `python -m geometry.cli.main` with `PYTHONPATH=src/python`, or reinstall with `pip install -e ".[dev]"`.
- If Maven uses an older JDK, verify `javac -version`; the project targets Java 11.
