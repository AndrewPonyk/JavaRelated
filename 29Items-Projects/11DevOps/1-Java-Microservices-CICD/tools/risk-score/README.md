# Deployment Risk Score

Estimates the probability that a change set causes a failed production deployment,
from **code metrics only** — no external services required.

## How it is used

| Consumer | Mode | Effect |
|---|---|---|
| `ci.yml` (every PR) | advisory | score + feature breakdown in the job summary |
| `cd-production.yml` | **gate** | `--fail-above 0.70` blocks promotion (override is logged) |
| Release engineer | guidance | `HIGH` (≥ 0.70) → blue-green; `MEDIUM` (≥ 0.40) → watch dashboards |

## Features

Extracted from `git diff --numstat base...head` (+ optional JaCoCo XML):

- change magnitude: lines added/deleted, files changed
- risk surfaces touched: Flyway migrations, Helm/ArgoCD config, CI workflows, build files
- quality signal: share of the diff that is test code; measured line coverage

## Model

1. **Trained** — `train_model.py` fits a scikit-learn logistic regression on
   historical deployments (`data/deployments.csv`) and saves `model.joblib`;
   `risk_score.py` picks it up automatically.
2. **Heuristic fallback** — hand-tuned logistic with the same features, stdlib-only,
   so CI never depends on pip. Weights are documented inline in `risk_score.py`.

```bash
python risk_score.py --demo                       # smoke test, no git needed
python risk_score.py --base origin/main --head HEAD
python risk_score.py --base <prod-sha> --head <candidate-sha> --fail-above 0.70
```

## Roadmap (PROJECT-PLAN Phase 3)

- [ ] Label collection: ArgoCD notification hook appends a row per prod deploy;
      `failed=1` when a rollback/incident references the release tag within 24h
- [ ] Retrain weekly in a scheduled workflow once ≥ 200 labeled rows exist
- [ ] Add features: deploy hour/day, time since last deploy, author change entropy,
      historical failure rate of touched packages
- [ ] Calibration + threshold review against actual rollback base rate
