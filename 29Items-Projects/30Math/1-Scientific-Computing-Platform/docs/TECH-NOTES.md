# Scientific Computing Platform — Technical Notes

**Status:** Draft v1 · **Last updated:** 2026-07-08
**Related:** [PROJECT-PLAN.md](./PROJECT-PLAN.md) · [ARCHITECTURE.md](./ARCHITECTURE.md)

---

## 3.1 CI/CD Pipeline Design

```mermaid
flowchart LR
    pr["PR opened"] --> lint["Lint & typecheck<br/>ruff · mypy · eslint · tsc"]
    lint --> test["Tests<br/>pytest (3.11/3.12 matrix) · vitest · nbmake smoke"]
    test --> build["Build check<br/>docker build backend+frontend"]
    build --> merge["merge to main"]
    merge --> dev["Auto-deploy DEV<br/>ECR push · ECS update · S3 sync"]
    tag["tag v*.*.*"] --> stg["Deploy STAGING"]
    stg --> gate{"manual approval<br/>(GitHub environment)"}
    gate --> prod["Deploy PROD"]
```

Workflow files (in `.github/workflows/`):

| File | Trigger | Purpose |
| --- | --- | --- |
| `ci.yml` | PR + push to `main` | lint → typecheck → test → docker build check |
| `deploy.yml` | tag `v*.*.*` or manual dispatch | build/push images to ECR, run Alembic migrations, roll ECS services, sync SPA to S3 + invalidate CloudFront |
| `ml-pipeline.yml` | weekly cron + manual dispatch | upsert/execute the SageMaker Pipeline; endpoint deploy gated by the `ml-prod` environment |

Design choices that matter:

- **GitHub OIDC → AWS role assumption.** No static `AWS_ACCESS_KEY_ID` secrets;
  each workflow gets `permissions: id-token: write` and assumes a least-privilege
  role per environment.
- **`concurrency` groups** cancel superseded PR runs; deploys serialize per environment.
- **Caching**: `astral-sh/setup-uv` (Python deps keyed on `uv.lock`) and
  `actions/setup-node` npm cache — CI should be < 5 min.
- **Migrations run as a pipeline step** (one-off ECS task running
  `alembic upgrade head`) *before* the new task definition rolls, and every
  migration must be backward-compatible one release back (expand → migrate →
  contract) so rollback is always safe.
- **Notebook smoke tests** (`pytest --nbmake notebooks/`) run on a schedule +
  PRs touching `notebooks/` or `libs/` — teaching material must never rot.

> ⚠️ **Monorepo note:** this project currently lives in a subdirectory of a
> larger repo. GitHub only executes workflows from the **repository root**
> `.github/`. When this project is split into its own repository, the
> `.github/` folder here becomes the root one. Until then, treat these files
> as the source of truth to copy/merge upward.

## 3.2 Testing Strategy

Test pyramid, bottom-heavy on the math kernel:

| Layer | Tooling | Target / current |
| --- | --- | --- |
| `libs/sciengine` unit | pytest + **Hypothesis** | gate ≥ 90% (currently 91%, 103 tests) |
| Backend unit (services) | pytest against real primitives (sandbox, sqlite) | gate ≥ 80% (currently 93% combined, 68 tests) |
| Backend integration | Starlette `TestClient` over the real ASGI app; per-test **SQLite** files locally, the **Postgres service container** in CI (`TEST_DATABASE_URL`); Celery eager so queue→worker→persist is exercised inline | every endpoint happy + error path |
| Frontend unit | Vitest + React Testing Library + jsdom | components with logic (10 tests: solver states incl. 202, auth validation, client refresh flow) |
| E2E (Phase 3) | Playwright against the docker-compose stack | 3–5 student journeys, nightly + pre-release |
| Notebooks | `nbmake` (in CI on every PR touching `notebooks/`/`libs/`) | all `notebooks/**.ipynb` execute cleanly |

**Numerical code needs different test idioms than CRUD code:**

1. **Never `==` on floats.** Use `math.isclose` / `numpy.allclose` with
   explicit, justified tolerances (and remember `abs_tol` for values near 0).
2. **Golden analytic tests**: verify methods against closed-form answers
   (`∫₀^π sin = 2`, roots of factorable polynomials), not against
   previously-recorded program output.
3. **Property-based tests** encode *mathematical invariants*:

   ```python
   from hypothesis import given, strategies as st

   @given(st.floats(-10, 10), st.floats(-10, 10))
   def test_simpson_is_exact_for_linear_functions(a, b):
       lo, hi = sorted((a, b))
       result = integrate_function(lambda x: 3 * x + 1, lo, hi, method="simpson", n=64)
       exact = (1.5 * hi**2 + hi) - (1.5 * lo**2 + lo)
       assert math.isclose(result.value, exact, rel_tol=1e-9, abs_tol=1e-9)
   ```

4. **Security tests are unit tests**: `test_parsing.py` asserts that
   `__import__`, dunder access, and oversized inputs are *rejected* — these
   are regression tests for the platform's main attack surface.
5. **Symbolic equivalence, not string equality**: assert
   `sympy.simplify(actual - expected) == 0`, never `str(actual) == "x + 1"`
   (SymPy's printing order is not contractual).

## 3.3 Deployment Strategy

**Everything is a container; ML rides SageMaker; static assets ride CloudFront.**

| Deployable | Image | Runs on |
| --- | --- | --- |
| API | `backend/Dockerfile` (multi-stage uv, non-root) | ECS Fargate service behind ALB |
| Compute worker | same image, `celery worker` command | ECS Fargate service (scales on queue depth) |
| Frontend | `frontend/Dockerfile` (build) → static bundle | S3 + CloudFront (container only for local dev) |
| Jupyter kernel | scipy-notebook + `sciengine` (Phase 2) | SageMaker Studio custom image (ECR) |
| ML training | SageMaker sklearn container + `ml/training` | SageMaker training jobs / Pipeline |
| ML inference | SageMaker sklearn container + `ml/inference` | SageMaker **serverless** endpoint |

- **Environments**: `dev` (auto from `main`) → `staging` (tags) → `prod`
  (manual approval). One AWS account per environment if possible; at minimum
  separate VPCs + IAM boundaries.
- **Rollout**: ECS rolling deploy with circuit breaker (auto-rollback to the
  previous task definition on failed health checks). `/*healthz*` is liveness;
  `/readyz` gates traffic (checks DB/Redis reachability).
- **Rollback** = redeploy previous image tag (images are immutable,
  `scp-backend:<git-sha>`); DB rollback is *not* `alembic downgrade` in prod —
  it's the expand/contract discipline from §3.1.
- **SageMaker**: models are promoted through the **Model Registry**
  (`PendingManualApproval` → `Approved`); endpoint config changes are
  blue/green by nature (new endpoint config, then `UpdateEndpoint`).

## 3.4 Environment Management

Single source of truth: `app/core/config.py` (pydantic-settings). Precedence:

```
process env  >  .env file (local only)  >  code defaults
```

- Local: copy `.env.example` → `.env` (gitignored); docker-compose reads it too.
- AWS: non-secret config baked into task definitions; secrets referenced from
  Secrets Manager/SSM at `/scp/{env}/{key}` (e.g. `/scp/prod/jwt_secret_key`).
- Frontend build-time vars use the `VITE_` prefix and are injected in CI per
  environment (`VITE_API_BASE_URL`).
- **Never** commit `.env`; never log settings objects (the JWT secret is in there).

`.env.example` (kept in the repo root, copy-paste ready):

```dotenv
# --- Runtime ---
ENVIRONMENT=local            # local|dev|staging|prod
DEBUG=true
LOG_LEVEL=INFO

# --- Data stores ---
DATABASE_URL=postgresql+psycopg://scp:scp@localhost:5432/scp
REDIS_URL=redis://localhost:6379/0

# --- Auth ---
JWT_SECRET_KEY=change-me-in-anything-but-local
ACCESS_TOKEN_TTL_SECONDS=900
REFRESH_TOKEN_TTL_SECONDS=1209600

# --- CORS (JSON list) ---
CORS_ORIGINS=["http://localhost:5173"]

# --- AWS / ML ---
AWS_REGION=us-east-1
S3_ARTIFACTS_BUCKET=              # empty ⇒ local artifact store (ARTIFACTS_DIR)
SAGEMAKER_RECOGNIZER_ENDPOINT=    # empty ⇒ heuristic fallback

# --- Artifacts ---
ARTIFACTS_DIR=./artifacts         # shared api↔worker volume in docker-compose

# --- Compute limits & caching ---
SYNC_SOLVE_TIMEOUT_SECONDS=2.0    # API fast-path budget before 202/504
WORKER_OP_TIMEOUT_SECONDS=30.0    # sandbox budget for queued jobs
MAX_EXPRESSION_LENGTH=512
SOLVE_CACHE_TTL_SECONDS=3600
OMP_NUM_THREADS=1                 # keep BLAS from oversubscribing worker cores

# --- Rate limiting ---
RATE_LIMIT_PER_MINUTE=120         # POST /api/v1/* per identity; 0 disables

# --- Celery ---
CELERY_TASK_ALWAYS_EAGER=false    # true only in tests (tasks run inline)

# --- Frontend (Vite, build-time) ---
VITE_API_BASE_URL=
```

## 3.5 Version Control Workflow

**GitHub Flow (trunk-based with short-lived branches).**

- `main` is always deployable; every merge auto-deploys to **dev**.
- Branches: `feat/…`, `fix/…`, `docs/…`; lifetime measured in days; PRs small,
  reviewed, CI-green, squash-merged (linear history).
- Releases are **tags** (`v0.4.0`, SemVer) cut from `main` → staging → gated prod.
- Conventional Commits (`feat:`, `fix:`, `docs:`…) → mechanical changelogs.
- Protected `main`: required checks, required review, no force pushes;
  `CODEOWNERS` routes `libs/sciengine/**` changes to a math-kernel owner.

*Why not Gitflow*: long-lived `develop`/release branches add merge ceremony
that only pays off with parallel supported versions — a SaaS education
platform ships one version, continuously. *Why not pure trunk (commit to
main)*: correctness of numerical code benefits from mandatory review, and CI
matrix runs (two Python versions + notebooks) are too slow for pre-commit gating.

## 3.6 Common Pitfalls (this stack, specifically)

1. **`sympy.sympify` is `eval` in a trench coat.** Never feed it user input;
   use `sciengine.symbolic.parsing.parse_expression` (whitelist parser). Also
   ban `parse_latex` on untrusted input until vetted — it, too, constructs
   arbitrary functions.
2. **SymPy can hang, not just be slow.** `solve`/`integrate` may never return
   on adversarial input. Thread timeouts cannot kill it (GIL + C loops) and
   `signal.SIGALRM` doesn't exist on Windows — the only reliable guard is a
   **separate process** you can kill. Our worker design assumes this from day 1.
3. **Matplotlib on servers**: set the `Agg` backend and use the OO API
   (`matplotlib.figure.Figure`), never `pyplot`, in API/worker code —
   `pyplot` keeps global state, leaks figures, and is not thread-safe.
   First render on a fresh container is slow (font-cache build): warm it in
   the Docker image (`python -c "import matplotlib.pyplot"` at build time).
4. **BLAS thread explosion**: NumPy links OpenBLAS/MKL which defaults to
   one-thread-per-core; multiply by Celery concurrency and you oversubscribe.
   Set `OMP_NUM_THREADS=1` (and friends) in worker containers.
5. **`10**10**10`**: exponentiation of exact SymPy integers can allocate
   gigabytes during *parsing/evaluation*. Enforce complexity guards (op count,
   exponent caps) before evaluation, plus per-process memory rlimits.
6. **Notebooks in git**: raw `.ipynb` diffs (outputs, execution counts) are
   unreviewable and leak data. `nbstripout` runs in pre-commit; consider
   Jupytext pairing for code-review-friendly `.py` mirrors.
7. **LaTeX injection**: render math with KaTeX `trust: false` only. Never pipe
   user LaTeX to `pdflatex` (arbitrary file read/write via `\input`,
   `\openout`) outside a throwaway container with no mounts.
8. **Long HTTP requests die at the ALB** (default idle timeout 60 s). Don't
   raise the timeout — use the 202 + job pattern (§2.3) for anything that can
   exceed the sync budget.
9. **Floating-point test flakiness**: tolerance-free assertions pass on one
   BLAS build and fail on another (or between x86/ARM CI runners). Always
   tolerance-based comparisons; pin the manylinux wheel set via `uv.lock`.
10. **Training/serving skew**: featurization must be *identical* in
    `ml/training` and the API fallback — that's why both import
    `sciengine.ml.features`. Pin `numpy`/`scikit-learn` versions in the
    SageMaker containers to match `uv.lock`, and prefer non-pickle formats
    (ONNX / `skops`) for model artifacts — unpickling is code execution.
11. **SageMaker serverless cold starts** (seconds): acceptable for
    classification hints, not for the solve path — never put SageMaker in the
    critical solve loop; it's an enrichment (§2.6 fail-soft).
12. **Windows dev machines** (this team has them): no `SIGALRM`, no `fork` —
    which is why the sandbox (`sciengine.runtime`) uses the `spawn` context
    everywhere. Celery runs the **threads pool on all platforms** (prefork's
    daemonized children cannot spawn the sandbox subprocess, and the heavy
    math holds no GIL anyway). Guard entry points with
    `if __name__ == "__main__":`. CI runs Linux — test both when touching
    process management.
13. **CORS with credentials**: `allow_origins=["*"]` silently breaks
    `Authorization` headers; keep the explicit origin allowlist in config.
14. **Async SQLAlchemy foot-gun**: lazy-loading relationships after the
    session closes raises `MissingGreenlet`. Use `selectinload` /
    `expire_on_commit=False` (already configured) and return DTOs, not ORM
    objects, from services.
