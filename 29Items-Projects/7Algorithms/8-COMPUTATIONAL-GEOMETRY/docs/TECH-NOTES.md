# Technical Notes

## 3.1 CI/CD Pipeline Design

Even though this project does not deploy to a runtime environment, CI is still useful for keeping the Java and Python implementations aligned.

Recommended pipeline:

1. **Checkout**
   - Pull repository code.
   - Use cache for Maven and pip dependencies.
2. **Lint**
   - Python: Ruff.
   - Java: Maven compiler warnings and future Checkstyle/Spotless configuration.
3. **Test**
   - Python: pytest.
   - Java: JUnit 5 via Maven.
4. **Build**
   - Python: package metadata validation.
   - Java: `mvn package`.
5. **Publish or deploy**
   - Not applicable for normal learning use.
   - Future optional artifacts: executable JAR and Python wheel.

Branch gates should require tests before merging. Keep CI fast by using small deterministic fixtures for algorithm checks.

## 3.2 Testing Strategy

### Unit Testing

- **Python:** pytest.
- **Java:** JUnit 5.
- **Coverage target:** Enforce at least 80% Python coverage in CI with `coverage run -m pytest` and `coverage report --fail-under=80`.
- **Test style:** Use small geometry examples where the expected answer can be checked by inspection.

Recommended test categories:

- Orientation and epsilon comparisons.
- Convex hull with duplicate, collinear, triangle, square, and random-looking fixed inputs.
- Point in polygon with inside, outside, edge, and vertex cases.
- Closest pair with ties and duplicate points.
- Sweep line with crossing, touching, overlapping, and disjoint segments.
- Delaunay/Voronoi with simple triangles and square point sets.

### Integration Testing

Integration tests should execute the Java and Python CLI runners and assert that:

- The process exits successfully.
- The expected algorithm names appear.
- The output contains deterministic counts and key points.
- Degenerate examples produce documented messages.

### End-to-End Testing

No browser E2E tests are needed. For this project, E2E means running the local console demos end to end.

Potential tools:

- PowerShell script in `tools/run_all.ps1`.
- GitHub Actions matrix for Java and Python versions.
- Golden-output snapshots for stable demo cases once the output format settles.

## 3.3 Deployment Strategy

There is no deployment target. The project is meant for local execution.

Recommended local distribution paths:

- **Java:** Build an executable JAR with Maven.
- **Python:** Install in editable mode with `pip install -e .` and run `python -m geometry.cli.main`.
- **Visualization:** Run optional Matplotlib scripts locally.

Containerization is optional and limited to reproducible local checks. `docker-compose.yml` runs the Java and Python console demos/tests; it does not introduce a database, server, or deployment runtime.

## 3.4 Environment Management

Configuration should remain minimal and non-secret.

Use `.env.example` for documented defaults:

```dotenv
# Local-only input safety setting.
GEOMETRY_MAX_INPUT_POINTS=1000000
```

Guidelines:

- Keep deterministic defaults in source control.
- Do not put secrets in `.env` files.
- Prefer command-line flags over environment variables for algorithm-specific experiments.
- Keep Java and Python tolerance defaults aligned.
- Use `GEOMETRY_MAX_INPUT_POINTS` to cap untrusted local CSV/JSON input size.

## 3.5 Version Control Workflow

Use **GitHub Flow**:

- `main` is always buildable.
- Create short-lived feature branches.
- Open a pull request with focused changes.
- Require CI to pass before merge.

This is simpler than Gitflow and fits a small learning project without release trains, environments, or long-lived maintenance branches.

## 3.6 Common Pitfalls

- **Floating-point equality:** Direct `==` checks on coordinates will create unstable behavior. Use centralized epsilon helpers.
- **Collinear hull points:** Decide whether to keep or remove collinear boundary points and test that behavior.
- **Duplicate points:** Normalize input before algorithms that assume unique points.
- **Comparator instability:** Sorting points with epsilon-aware comparisons can violate comparator contracts if done casually. Prefer exact lexical sorting for deterministic ordering and use epsilon only for predicates.
- **Segment intersection edge cases:** Touching endpoints and overlapping collinear segments need explicit handling.
- **Point-in-polygon boundaries:** Return a distinct boundary result instead of forcing every point into inside/outside.
- **Voronoi complexity:** A production Fortune sweep implementation is complex. For learning, deriving Voronoi cells from Delaunay triangulation may be easier to explain.
- **Language drift:** Keep Java and Python fixtures equivalent so results stay comparable.
- **Overbuilding infrastructure:** Avoid backend, database, and frontend code unless the project scope changes. Docker Compose is only a local verification wrapper.
