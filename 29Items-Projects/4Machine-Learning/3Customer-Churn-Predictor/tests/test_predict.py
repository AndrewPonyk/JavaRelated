"""End-to-end prediction + explanation tests (slow: depends on a trained model)."""

from __future__ import annotations

import pandas as pd
import pytest

from churn_predictor import ModelArtifactError, PredictionError
from churn_predictor.models.explain import explain_one, global_importance
from churn_predictor.models.predict import predict_frame, predict_one
from churn_predictor.retention.recommendations import recommend

pytestmark = pytest.mark.slow

_RECORD = {
    "customer_id": "CUST-9999",
    "tenure_months": 3,
    "monthly_charges": 95.0,
    "total_charges": 285.0,
    "num_support_tickets": 6,
    "contract_type": "Month-to-month",
    "payment_method": "Electronic check",
}


def test_predict_one_contract(trained_artifact: str) -> None:
    pred = predict_one(_RECORD, artifact_path=trained_artifact)
    assert 0.0 <= pred.probability <= 1.0
    assert pred.label in (0, 1)
    assert pred.risk_band in {"low", "medium", "high"}


def test_predict_frame_adds_columns(
    trained_artifact: str, synthetic_customers: pd.DataFrame
) -> None:
    scored = predict_frame(synthetic_customers.head(20), artifact_path=trained_artifact)
    assert "churn_probability" in scored.columns
    assert "risk_band" in scored.columns
    assert scored["churn_probability"].between(0, 1).all()


def test_predict_empty_raises(trained_artifact: str) -> None:
    with pytest.raises(PredictionError):
        predict_frame(pd.DataFrame(), artifact_path=trained_artifact)


def test_missing_artifact_raises(tmp_path) -> None:
    with pytest.raises(ModelArtifactError):
        predict_one(_RECORD, artifact_path=str(tmp_path / "does_not_exist.joblib"))


def test_explain_and_recommend(trained_artifact: str) -> None:
    drivers = explain_one(pd.DataFrame([_RECORD]), top_n=5, artifact_path=trained_artifact)
    assert 1 <= len(drivers) <= 5
    actions = recommend(drivers)
    assert actions  # always at least the default action


def test_global_importance(trained_artifact: str, synthetic_customers: pd.DataFrame) -> None:
    imp = global_importance(synthetic_customers.head(100), artifact_path=trained_artifact)
    assert {"feature", "mean_abs_shap"} <= set(imp.columns)
    assert (imp["mean_abs_shap"] >= 0).all()
