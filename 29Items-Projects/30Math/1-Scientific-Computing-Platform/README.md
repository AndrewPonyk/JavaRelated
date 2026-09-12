# Scientific Computing Platform

A multi-purpose scientific computing engine for education: numerical methods
and symbolic math (NumPy · SciPy · SymPy), interactive Jupyter computations,
a React UI with LaTeX rendering (KaTeX), asynchronous compute jobs, and
ML-assisted equation pattern recognition served from AWS SageMaker.

**Start here:**

| Doc | What's inside |
| --- | --- |
| [`docs/PROJECT-PLAN.md`](docs/PROJECT-PLAN.md) | Repo layout rationale + phase status (Phases 1–2 implemented) |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Pattern, diagrams, data flow, scalability, security, error contract |
| [`docs/TECH-NOTES.md`](docs/TECH-NOTES.md) | CI/CD, testing strategy, deployment, environments, stack pitfalls |

## Functionality — what the app can do

**Accounts**
- Register with email + password and sign in / sign out
- Stay signed in safely: short-lived access tokens with rotating, single-use refresh tokens (server-side revocation — logout actually kills the session)
- View the current profile (`/auth/me`); roles: student / instructor / admin

**Solve math interactively (web UI or API)**
- Solve equations symbolically and get exact roots: `x^2 - 4 = 0` → `x = -2, x = 2`
- Show **step-by-step derivations** (canonical form → factoring or quadratic formula → roots), rendered as LaTeX with an expandable "Show steps" panel
- Differentiate: `d/dx x^3` → `3x^2` (any order up to 10)
- Integrate symbolically: `∫ 2x dx` → `x^2` (non-elementary integrals return their closed form, e.g. `erf`)
- Compute limits: `lim x→0 sin(x)/x` → `1` (one-sided, and at `oo`)
- Expand Taylor series: `exp(x)` around 0 → `1 + x + x²/2 + x³/6 + …`
- Live-preview any typed expression as LaTeX while typing (`sqrt(x)/2` → `\frac{\sqrt{x}}{2}`)
- Plot any function as an SVG: `sin(x)/x` on `[-15, 15]` (poles become gaps, not errors)

**Run heavy work as background jobs**
- Submit long computations (equation solve, integral, ODE, plot, classification) to a worker queue and watch them go `queued → running → succeeded/failed` live in the UI
- Solve an ODE numerically: `dy/dt = -y`, `y(0)=1` → sampled solution curve
- Render large plots on a worker and download the stored SVG artifact
- Automatic escalation: an interactive solve that exceeds the ~2 s budget becomes a background job (`202` + id) when signed in, instead of timing out
- List past computations (paginated), inspect results, delete them — each user sees only their own

**ML pattern recognition**
- Classify an expression's pattern: `3x^2 + 2x - 1` → `quadratic`, `sin(x)+1` → `trigonometric` (linear / quadratic / polynomial / trig / exponential / logarithmic / rational)
- Served by a trained SageMaker model when configured, with a built-in heuristic fallback (`source` field says which answered)

**Numerical methods library (used by notebooks and the API alike)**
- Find roots: bisection, Newton (with per-iteration traces for teaching), Brent
- Integrate numerically: trapezoid / Simpson with error estimates, adaptive quadrature, improper integrals (`∫₀^∞ e⁻ˣ dx = 1`)
- Solve ODEs: production `solve_ivp` wrapper plus hand-rolled Euler / RK4 teaching steppers
- Interpolate: Lagrange polynomials (stable barycentric form), cubic splines
- Linear algebra: solve `Ax = b`, eigendecomposition with the characteristic polynomial in LaTeX

**Jupyter teaching material**
- Run the bundled notebooks (getting-started + instructor lesson template) against the same math kernel the API uses — locally or in the compose Jupyter service on :8888

**Built-in platform behaviors (invisible but load-bearing)**
- Identical questions are cached by canonical form — 30 students asking `x^2-4=0` cost one solve (`cached: true`)
- Rate limiting per user/IP on all POST endpoints (429 + `Retry-After`)
- Hostile input is rejected safely: expressions are parsed by a whitelisting parser (no `eval`), and anything long-running executes in a killable sandbox process
- Every error is a structured `problem+json` with a correlation `request_id`; `/healthz` and `/readyz` report service health

## Math catalog — problems, methods, and cases the app can resolve

Every example style below is exercised by the test suite.

### 1. Equation solving (exact, symbolic) — `POST /symbolic/solve`

- **Linear**: `2x + 6 = 0` → `x = −3` (with isolation steps)
- **Quadratic, factorable**: `x^2 − 4 = 0` → `(x−2)(x+2) = 0` → `x = ±2`
- **Quadratic, irreducible**: `x^2 + x − 1 = 0` → exact radicals via discriminant + quadratic formula steps
- **Higher-degree polynomials**: cubics/quartics where exact roots exist, e.g. `x^3 − 6x^2 + 11x − 6 = 0` → `1, 2, 3`
- **Rational**: `(x+1)/(x−1) = 2` · **Radical**: `sqrt(x) = 3` · **Exponential/log**: `exp(x) = 2` → `log(2)`, `log(x) = 1` → `E`
- **Trigonometric**: `sin(x) = 0`, `sin(x) = 1/2` (principal solutions)
- **Bare expressions** imply `= 0`: `x − 3` → `x = 3`
- **Systems** (library/worker): up to 8 equations / 8 unknowns, e.g. `x+y=3, x−y=1` → `{x: 2, y: 1}`; nonlinear systems where SymPy finds closed forms
- Honest limit: equations with no symbolic method (exotic transcendental mixes) return a clean "no solver available" `422`, never a hang — the sandbox kills anything that stalls.

### 2. Calculus (symbolic)

- **Derivatives** (`/differentiate`): any composition of the whitelisted functions, chain/product/quotient rules, order 1–10 — `d²/dx² sin(x)` → `−sin(x)`
- **Indefinite integrals** (`/integrate`): polynomials, trig, exp/log, rational functions; non-elementary ones return special functions — `∫ e^(−x²) dx` → `(√π/2)·erf(x)`
- **Limits** (`/limit`): finite points, one-sided `+`/`−`, at infinity, indeterminate forms — `lim x→0 sin(x)/x = 1`, `lim x→∞ (2x+1)/x = 2`
- **Taylor series** (`/series`): around any point, order 1–12 — `exp(x)` → `1 + x + x²/2 + x³/6 + …`
- **LaTeX rendering** (`/render`): any expression → canonical LaTeX, live while typing

### 3. Plotting — `POST /plots/function` or background job

- Any single-variable function on an interval, up to 5000 samples → SVG
- Handles poles/domain gaps gracefully: `1/x`, `tan(x)`, `sin(x)/x` render with breaks, not crashes

### 4. Numerical root finding (library + notebooks)

- **Bisection** — guaranteed on a sign change: `x² − 2` on `[0, 2]` → `√2` to 1e-12
- **Newton–Raphson** — quadratic convergence, analytic or finite-difference derivative, per-iteration traces for teaching: `cos(x) = x` → the Dottie number 0.739085…
- **Brent** — production method (superlinear + robust)

### 5. Numerical integration

- **Composite trapezoid / Simpson** with Richardson error estimates: `∫₀^π sin = 2`
- **Adaptive quadrature** (QUADPACK) for hard integrands
- **Improper integrals**: `∫₀^∞ e^(−t) dt = 1`

### 6. Differential equations (IVPs)

- Any first-order system `dy/dt = f(t, y)` — RK45 with automatic **Radau fallback for stiff problems**, tight tolerances
- Teaching steppers: explicit Euler and classic RK4 (the tests demonstrate RK4 beating Euler by >100× at equal step)
- Via API/background job: text-form scalar ODEs like `dy/dt = −y, y(0) = 1` → sampled solution matching `e^(−t)`

### 7. Interpolation

- **Lagrange polynomial** through up to 30 distinct points (stable barycentric evaluation + explicit coefficients) — exactly reproduces a parabola through 3 points
- **Cubic splines**: natural / clamped / not-a-knot boundary conditions

### 8. Linear algebra

- **Solve Ax = b** (singular matrices detected with a clear error)
- **Eigendecomposition**: symmetric matrices → real spectrum via `eigh` (`[[2,1],[1,2]]` → `λ = 1, 3`); general matrices incl. complex eigenvalues (rotation matrix → `±i`); condition number; characteristic polynomial in LaTeX for matrices up to 6×6

### 9. ML pattern recognition — `POST /ml/classify`

Classifies expressions into **linear, quadratic, polynomial (deg ≥ 3), trigonometric, exponential, logarithmic, rational** — e.g. `3x² + 2x − 1` → quadratic (0.9), `(x+1)/(x−1)` → rational.

### Input notation the parser understands

Classroom style: `x^2` (caret), implicit multiplication `2x`, `3(x+1)`, `5!`; functions `sin cos tan asin acos atan atan2 sinh cosh tanh exp log ln sqrt abs sign floor ceiling factorial gamma Min Max`; constants `pi, E, I, oo`. Deliberate refusals (safety, not gaps): `10^10^10`-style bombs, 30+-digit literals, deep nesting, anything resembling code.

## Layout (short version)

```text
libs/sciengine/   ★ framework-free math kernel + the killable compute sandbox
backend/          FastAPI API + Celery compute workers + Alembic migrations
frontend/         React 19 + TypeScript + Vite SPA (KaTeX, auth, job tracking)
notebooks/        Jupyter teaching material (executed by CI)
ml/               SageMaker training / inference / pipeline / promotion
.github/          CI, deploy, and ML workflows
```

## Run everything with Docker (recommended first contact)

Prereq: Docker Desktop.

```bash
docker compose up --build
```

That brings up Postgres, Redis, a one-shot migration job (`alembic upgrade
head`), the API on **:8000**, a compute worker, the frontend dev server on
**:5173**, and Jupyter Lab on **:8888**. No `.env` needed for local defaults
(copy `.env.example` → `.env` to customize).

Try it: open http://localhost:5173 → type `x^2 - 4 = 0` → **Solve** (expand
*Show steps* for the derivation). Register an account, then submit a
background job under **My computations** — a plot job renders on a worker and
streams back through the artifact endpoint.

Or from the terminal:

```bash
curl -X POST localhost:8000/api/v1/symbolic/solve \
  -H 'Content-Type: application/json' \
  -d '{"expression": "x^2 - 5x + 6 = 0"}'
# → {"solutions": ["2", "3"], "steps_latex": [...], ...}

curl -X POST localhost:8000/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email": "ada@example.edu", "password": "hyperbolic-8"}'
```

OpenAPI docs: http://localhost:8000/docs · liveness `/healthz` · readiness `/readyz`.

## Local development (without containers)

Prereqs: [uv](https://docs.astral.sh/uv), Node 22+, Docker (for db/redis only).

```bash
cp .env.example .env
make install         # uv sync --all-packages + npm install
make up              # postgres + redis in docker
make migrate         # alembic upgrade head
make dev-api         # FastAPI on :8000 (reload)
make worker          # compute worker (threads pool) — separate terminal
make dev-fe          # Vite on :5173 (proxies /api to :8000)
make notebook        # Jupyter Lab over notebooks/
```

## Tests & quality gates

```bash
make test            # sciengine (103 tests, gate ≥90% cov) + backend (68 tests, gate ≥80%)
make test-fe         # vitest + Testing Library (10 tests)
make lint            # ruff + eslint
make typecheck       # mypy + tsc
uv run pytest --nbmake notebooks/   # execute the teaching notebooks
```

CI (`.github/workflows/ci.yml`) runs all of the above on a Python 3.11/3.12
matrix, with the backend suite against real Postgres/Redis service containers.

## API surface (v1)

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register` · `/auth/login` · `/auth/refresh` (rotating) · `/auth/logout` · `GET /auth/me` |
| Symbolic | `POST /symbolic/solve` (200, or **202 → background job** past the sync budget) · `/differentiate` · `/integrate` · `/limit` · `/series` · `/render` |
| Plots | `POST /plots/function` → SVG |
| ML | `POST /ml/classify` (SageMaker endpoint or heuristic fallback — `source` tells the truth) |
| Computations | `POST/GET/DELETE /computations…` + `GET /computations/{id}/artifact` (owner-scoped) |
| Probes | `GET /healthz` · `GET /readyz` |

Errors are RFC 7807 `application/problem+json` with request-ID correlation.
POST endpoints are rate-limited per identity (`RATE_LIMIT_PER_MINUTE`, default
120) — expect `429` + `Retry-After` past the budget. Responses over 1 KiB are
gzip-compressed. Interactive schema: http://localhost:8000/docs.

### Example: solve

```bash
curl -X POST localhost:8000/api/v1/symbolic/solve \
  -H 'Content-Type: application/json' \
  -d '{"expression": "x^2 - 4 = 0", "variable": "x"}'
```

```json
{
  "equation_latex": "x^{2} - 4 = 0",
  "variable": "x",
  "solutions": ["-2", "2"],
  "solutions_latex": ["-2", "2"],
  "steps_latex": ["x^{2} - 4 = 0", "\\left(x - 2\\right) \\left(x + 2\\right) = 0", "x = -2, \\; x = 2"],
  "derivation_latex": "\\begin{aligned}\n& x^{2} - 4 = 0 \\\\\n…\\end{aligned}",
  "cached": false
}
```

A solve that exceeds the sync budget returns `202 {"computation_id": …}` for
authenticated callers (poll `GET /computations/{id}`), or a `504` problem with
guidance when anonymous.

### Example: background job

```bash
TOKEN=$(curl -s -X POST localhost:8000/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email": "ada@example.edu", "password": "hyperbolic-8"}' | jq -r .access_token)

curl -X POST localhost:8000/api/v1/computations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title": "plot sinc", "kind": "plot",
       "input_payload": {"expression": "sin(x)/x", "x_min": -12, "x_max": 12}}'
# → 201 {"id": "…", "status": "queued", …}; then:
#   GET /api/v1/computations/{id}            → status/result
#   GET /api/v1/computations/{id}/artifact   → the rendered SVG
```

### Example: error shape

```json
{
  "type": "https://scp.example.com/problems/expression_parse_error",
  "title": "expression parse error",
  "status": 422,
  "detail": "Expression contains unsupported characters. Allowed: …",
  "request_id": "8f4a1c2e…"
}
```

## Troubleshooting

| Symptom | Cause & fix |
| --- | --- |
| `docker compose …` hangs with no output | Docker Desktop isn't running yet — start it and retry; commands block until the daemon is up. |
| `/readyz` returns 503 `{"redis": "error: …"}` | Redis (or Postgres) is unreachable. Locally: `make up`. The API still serves solves — readiness gating is the point. |
| Solve returns 504 `computation_timeout` | The equation blew the interactive budget (`SYNC_SOLVE_TIMEOUT_SECONDS`). Sign in and retry — it becomes a background job (202) — or simplify the input. |
| 429 `rate_limited` during load tests | Working as intended (`RATE_LIMIT_PER_MINUTE`, default 120/min per identity). Set `0` to disable locally. |
| First plot request is slow after startup | Matplotlib builds its font cache once per container; the Docker image pre-warms it, bare-metal runs pay it on first render. |
| Background jobs stay `queued` forever | No worker is consuming the queue: `make worker` (or check the `worker` compose service). A broker outage marks new jobs `failed (queue_unavailable)` instead. |
| `Ports are not available: 8000/5432/5173` | Another process owns the port. Stop it or remap in `docker-compose.yml`. |
| Windows: worker crashes at startup with a pool error | Use the threads pool (the Makefile/compose commands already do): `celery … --pool=threads`. Prefork can't spawn the sandbox subprocess. |
| Tests: `database is locked` on SQLite | A previous run died mid-write; re-run — each test gets a fresh tmp DB file. CI uses real Postgres (`TEST_DATABASE_URL`). |
| API in staging/prod refuses to boot: `JWT_SECRET_KEY still has its placeholder value` | Intentional guard — set a real secret via Secrets Manager (`/scp/{env}/jwt_secret_key`). |
| `curl localhost` hangs on this machine but the server is up | A system-wide HTTP proxy is intercepting loopback traffic; use `curl --noproxy '*' …`. |

## The one security rule everyone must know

`sympy.sympify` calls `eval()` and is **banned on user input**. The only
sanctioned parser for untrusted math is
`sciengine.symbolic.parsing.parse_expression` (two-pass: complexity-guarded
before anything is evaluated), and anything that can run unboundedly executes
inside the killable sandbox subprocess (`sciengine.runtime`). Details and the
rest of the threat model: [`docs/ARCHITECTURE.md` §2.5](docs/ARCHITECTURE.md).
