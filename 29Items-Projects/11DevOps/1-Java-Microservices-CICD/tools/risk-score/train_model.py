#!/usr/bin/env python3
"""Trains the deployment-risk model from historical deployment outcomes.

Data contract (see data/deployments.sample.csv):
    one row per production deployment, features as computed by risk_score.py
    plus the label `failed` (1 = rollback/incident within 24h, 0 = healthy).

Label collection (TODO, Phase 3 — see tools/risk-score/README.md):
  * Argo CD notifications → append a row on every prod sync
  * mark `failed=1` when a rollback commit / `argocd app rollback` / Sev1-2
    incident references the release tag within 24h

Usage:
    pip install -r requirements.txt
    python train_model.py --data data/deployments.csv --out model.joblib
"""

from __future__ import annotations

import argparse
from pathlib import Path

FEATURE_COLUMNS = [
    "total_lines", "files_changed", "touches_migrations", "touches_ci",
    "touches_helm", "touches_build", "test_ratio", "coverage",
]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, default=Path(__file__).parent / "data" / "deployments.sample.csv")
    parser.add_argument("--out", type=Path, default=Path(__file__).parent / "model.joblib")
    args = parser.parse_args()

    # Heavy deps imported lazily so risk_score.py stays stdlib-only.
    import joblib
    import pandas as pd
    from sklearn.linear_model import LogisticRegression
    from sklearn.model_selection import cross_val_score
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    frame = pd.read_csv(args.data)
    x = frame[FEATURE_COLUMNS]
    y = frame["failed"]

    # TODO(phase3): once >200 labeled rows exist, evaluate gradient boosting and
    #               calibrate probabilities (CalibratedClassifierCV); keep the
    #               logistic baseline as the interpretable reference.
    model = make_pipeline(StandardScaler(), LogisticRegression(max_iter=1000, class_weight="balanced"))

    if len(frame) >= 20:
        scores = cross_val_score(model, x, y, cv=min(5, max(2, len(frame) // 10)), scoring="roc_auc")
        print(f"cv ROC-AUC: {scores.mean():.3f} ± {scores.std():.3f} (n={len(frame)})")
    else:
        print(f"warning: only {len(frame)} rows — model will be unreliable; heuristic fallback may be better")

    model.fit(x, y)
    joblib.dump(model, args.out)
    print(f"saved {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
