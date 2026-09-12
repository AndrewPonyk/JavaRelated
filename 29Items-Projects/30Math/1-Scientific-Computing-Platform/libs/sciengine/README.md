# sciengine

The scientific kernel of the Scientific Computing Platform: numerical methods
(NumPy/SciPy), symbolic math (SymPy), headless plotting (Matplotlib), and ML
featurization. Framework-free by design — consumed by the FastAPI backend,
Celery workers, Jupyter notebooks, and SageMaker training code.

Security note: `sciengine.symbolic.parsing.parse_expression` is the **only**
sanctioned way to turn untrusted text into SymPy objects. `sympy.sympify` is
banned on user input (it calls `eval`). See `docs/ARCHITECTURE.md` §2.5.

```python
from sciengine.symbolic.solver import solve_equation

result = solve_equation("x^2 - 4 = 0")
result.solutions          # ['-2', '2']
result.solutions_latex    # ['-2', '2']
```

Run tests: `uv run pytest libs/sciengine/tests` (from the repo root).
