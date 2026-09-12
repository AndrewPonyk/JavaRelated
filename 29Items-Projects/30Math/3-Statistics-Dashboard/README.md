# 📊 Statistics Dashboard

Self-service statistical analysis with Streamlit, backed by PostgreSQL and deployed on
Streamlit Cloud via GitHub Actions. Built for analysts and PMs who need correct,
explained statistics — not just charts.

## What the app can do

**🧪 Hypothesis Testing (automated test selection)**
- Picks the right statistical test automatically from the data's characteristics — Student's t, Welch's t, paired t, Mann-Whitney U, Wilcoxon signed-rank, one-way ANOVA, Welch's ANOVA, Kruskal-Wallis, χ², Fisher's exact, or two-proportion z
- Shows the selection rationale verbatim (normality via Shapiro-Wilk, variance homogeneity via Levene, expected cell counts, CLT reasoning, sample sizes)
- Reports an effect size next to every p-value (Cohen's d, Hedges' g, rank-biserial, Cliff's δ, Cramér's V, η²) with a magnitude label
- Runs Holm-corrected pairwise follow-ups automatically after a significant result across 3+ groups
- Explains the verdict in plain language and lists every caveat (dropped missing rows, small samples, positional pairing)
- Exports any result as a one-click, self-contained HTML report

**⚖️ A/B Testing**
- Analyzes conversion experiments: two-proportion z-test with a 95% CI on the lift (absolute and relative)
- Adds a Bayesian companion: P(B beats A), expected lift, credible interval, and posterior-density chart (Beta-Binomial)
- Guards against broken experiments: sample-ratio-mismatch (SRM) check runs before any verdict
- Warns on interim looks when results are checked before a pre-registered sample size (anti-peeking)
- Plans experiments: power-based sample-size calculator (baseline rate + MDE + α + power → n per variant)
- Logs results to a registered experiment together with an exact snapshot of the analyzed data

**📈 Regression Analysis**
- Fits OLS (continuous outcome) and logistic (binary outcome) models with a full coefficient table (estimates, standard errors, p-values, confidence intervals)
- Reports R²/adjusted R² or pseudo-R², plus Durbin-Watson, Breusch-Pagan, and Jarque-Bera diagnostics
- Draws residuals-vs-fitted and normal Q-Q charts for assumption checking
- Screens for multicollinearity with a VIF table and warns when it bites
- Handles awkward column names safely and blocks formula injection

**📉 Distribution Fitting**
- Fits candidate distributions (normal, lognormal, exponential, gamma, Weibull, beta) to any numeric column
- Skips candidates whose support doesn't match the data (e.g. beta outside (0,1)) instead of failing
- Ranks fits by AIC with KS statistics as diagnostics, shows fitted PDFs over the histogram and a Q-Q plot of the best fit

**🔍 Data Explorer**
- Summary statistics for every column, missingness chart, and group-by aggregation tables
- Pearson correlation matrix with a diverging heatmap
- Column-kind overrides: correct the automatic classification (e.g. mark an int-coded Likert scale **ordinal** so rank-based tests are used)

**💾 Data & experiment management**
- Ships with a seeded demo dataset — every feature works with zero setup (demo mode, no database needed)
- Validated CSV upload (type, size cap, parse, shape checks) with human-readable rejections
- Saved-dataset library: parquet snapshots in PostgreSQL, deduplicated by content hash, with load/delete from the UI
- Experiment registry: create experiments with hypothesis, metric, planned sample size, and traffic split; forward-only lifecycle (draft → running → completed)
- Analysis history: every run on a saved dataset is recorded (test, parameters, results) and shown on Home
- Frames beyond 200k rows are analyzed on a seeded sample with a visible banner instead of an OOM

**🛡️ Behind the scenes**
- Friendly error handling everywhere — typed errors render as actionable messages, never stack traces
- Validated, colorblind-safe chart palette with light and dark token sets; WebGL for large scatters
- Production JSON logging, optional Sentry error tracking, weekly stale-data retention job

## Quickstart (demo mode — no database needed)

```bash
python -m venv .venv && . .venv/Scripts/activate   # Windows (Git Bash); use .venv/bin/activate on *nix
python -m pip install -r requirements-dev.txt
python scripts/seed_demo_data.py                   # regenerate bundled sample data (idempotent)
streamlit run streamlit_app.py
```

With no `DATABASE_URL` configured the app runs fully in-memory on bundled sample
data — every analysis works end-to-end, nothing is persisted.

## Full stack (with PostgreSQL)

```bash
docker compose -f docker/docker-compose.yml up -d db
cp .env.example .env                    # DATABASE_URL already points at the compose db
alembic upgrade head                    # or: python scripts/init_db.py
python scripts/seed_demo_data.py --to-db   # optional: register the demo data in the library
streamlit run streamlit_app.py
```

This unlocks the saved-dataset library, the ⚗️ Experiments registry, experiment result
logging (with data snapshots), and the Home page's recent-analyses panel.
For a lighter local setup without Docker, `DATABASE_URL=sqlite:///./local-dev.db` works too.

## Tests & quality

```bash
python -m pytest -m "not integration"   # unit + e2e (no DB required) — CI gates coverage at 75%
python -m pytest                        # everything (needs TEST_DATABASE_URL)
ruff check . && ruff format --check . && mypy app
```

The e2e layer drives the real app headlessly (`streamlit.testing.v1.AppTest`): every page
renders, and the main flows are exercised through actual widget interactions.

## Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Banner "Running in **demo mode**" and no library/experiments | No `DATABASE_URL` resolved. Copy `.env.example` → `.env` (local) or set it in Streamlit Cloud's Secrets. Restart the app — settings are read once at startup. |
| `connection refused` to PostgreSQL | The compose database isn't up: `docker compose -f docker/docker-compose.yml up -d db`, then `alembic upgrade head`. On managed PG, keep `?sslmode=require` in the DSN. |
| `relation "datasets" does not exist` | Migrations not applied to that database: `alembic upgrade head` (or `python scripts/init_db.py`). |
| Port 8501 already in use | `streamlit run streamlit_app.py --server.port 8502`, or stop the other Streamlit process. |
| Upload rejected | Only `.csv` up to `MAX_UPLOAD_MB` (default 25 MB) with ≥2 columns is accepted; the error message states which rule failed. |
| "analyzing a seeded random sample" banner | The frame exceeds `MAX_ANALYSIS_ROWS` (default 200k). Raise the limit in `.env` if your machine has the memory. |
| Tests can't find `app` module | Run `pytest` from the repository root — `pythonpath = ["."]` is configured in `pyproject.toml`. |
| Results not appearing in "Recent analyses" | Ad-hoc data isn't persisted by design. Click **💾 Save to library** first (or load a saved dataset) so runs have a dataset to attach to. |

More stack-specific pitfalls (Streamlit rerun model, Cloud limits, statistics traps) are
catalogued in [docs/TECH-NOTES.md §3.6](docs/TECH-NOTES.md).

## Documentation

| Doc | Contents |
|---|---|
| [docs/PROJECT-PLAN.md](docs/PROJECT-PLAN.md) | Repo structure, layering rules, implementation status |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture pattern, diagrams, data flow, security, error philosophy |
| [docs/TECH-NOTES.md](docs/TECH-NOTES.md) | CI/CD design, testing strategy, deployment, environments, pitfalls |

## Deployment

Pushes to `main` auto-deploy to **Streamlit Cloud** (main file `streamlit_app.py`,
dependencies from `requirements.txt`, secrets via the app's Secrets UI — see
`.streamlit/secrets.toml.example`) and are verified by
`.github/workflows/post-deploy-smoke.yml`. A weekly retention workflow prunes stale,
unreferenced datasets. The Docker image in `docker/` is the self-host fallback and is
build-verified in CI. Optional Sentry error tracking activates when `SENTRY_DSN` is set.
