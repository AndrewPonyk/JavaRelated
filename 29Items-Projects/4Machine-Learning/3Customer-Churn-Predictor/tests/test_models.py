"""Tests for feature engineering and the model training/serving contract.

The full training/serving tests are marked ``slow`` (they fit boosters) and are
excluded from the fast PR lane (`pytest -m "not slow"`).
"""

from __future__ import annotations

import pandas as pd
import pytest

from churn_predictor.features.engineering import build_features


def test_build_features_adds_expected_columns(synthetic_customers: pd.DataFrame) -> None:
    out = build_features(synthetic_customers)
    assert "tenure_bucket" in out.columns
    assert "avg_monthly_spend" in out.columns
    assert "tickets_per_month" in out.columns
    assert len(out) == len(synthetic_customers)  # engineering must not drop rows


def test_usage_ratios_no_divide_by_zero() -> None:
    df = pd.DataFrame({"tenure_months": [0], "total_charges": [100.0], "num_support_tickets": [3]})
    out = build_features(df)
    assert out["avg_monthly_spend"].notna().all()
    assert out["tickets_per_month"].notna().all()


@pytest.mark.slow
def test_train_model_produces_artifact(tmp_path, synthetic_customers: pd.DataFrame) -> None:
    """End-to-end training smoke test. Asserts artifact bundle + reasonable AUC."""
    import joblib

    from churn_predictor.models.train import train_model

    artifact = tmp_path / "model.joblib"
    meta = train_model(
        synthetic_customers,
        algorithms=("xgboost",),
        artifact_path=str(artifact),
        n_trials=3,
    )
    assert artifact.exists()
    assert meta.metrics["holdout_roc_auc"] > 0.6  # signal is learnable in fixture

    bundle = joblib.load(artifact)
    assert set(bundle) >= {"model", "preprocessor", "metadata"}
