# Backtracking Algorithms — Technical Notes

## 3.1 CI/CD Pipeline Design

A minimal GitHub Actions workflow runs on every push / PR:

```
lint (checkstyle / ruff) → compile Java → run Java → lint Python → run Python
```

No deployment stage — this project is local-only.
See `.github/workflows/ci.yml` for the full definition.

## 3.2 Testing Strategy

| Language   | Framework  | Target coverage |
|------------|------------|-----------------|
| Java       | JUnit 5    | ≥ 80 %          |
| Python     | pytest     | ≥ 80 %          |

### What to test
- **Correctness** — known solutions (e.g., N-Queens n=4 has exactly 2 solutions).
- **Edge cases** — n=0, n=1, empty graph, impossible puzzle.
- **Performance** — assert solve time < threshold for small inputs.

### Example test

```java
// Java
@Test void nQueensSize4Has2Solutions() {
    var solver = new NQueens(4);
    assertEquals(2, solver.solve().size());
}
```

```python
# Python
def test_n_queens_size_4():
    solver = NQueens(4)
    assert len(solver.solve()) == 2
```

## 3.3 Deployment Strategy

No deployment required.  To run locally:

```bash
# Java
javac java/src/**/*.java -d out/
java -cp out/ Main

# Python
python python/main.py
```

## 3.4 Environment Management

No external services or secrets.  The `.env.example` file documents optional
configuration:

```bash
# Algorithm defaults
DEFAULT_BOARD_SIZE=8
MAX_SOLUTIONS=0          # 0 = unlimited
VERBOSE=false
TIMEOUT_SECONDS=60
```

Load it in Python with `python-dotenv` (optional); Java reads system properties.

## 3.5 Version Control Workflow

**Trunk-based development** (simplest for a solo / small learning project):

- `master` — always deployable.
- Feature branches (`feat/nqueens`, `feat/sudoku`) merged via squash commit.
- No release branches needed.

Commit message convention: `feat:`, `fix:`, `docs:`, `refactor:`.

## 3.6 Common Pitfalls

| Pitfall                                  | Mitigation                                         |
|------------------------------------------|----------------------------------------------------|
| Stack overflow on large recursion depth  | Set reasonable defaults; warn if n > 20            |
| Off-by-one in board indexing             | Use 0-based indexing consistently                  |
| Mutable state shared between solutions   | Deep-copy or reset board between runs              |
| Python recursion limit (default 1000)    | `sys.setrecursionlimit()` for deep backtracks      |
| Confusing permutation vs combination     | Clear naming + unit tests distinguishing them       |
| Knight's Tour infinite loop on bad start | Detect no-solution case and terminate gracefully   |
