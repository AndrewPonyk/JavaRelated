"""Tuning tests across all three algorithms (slow)."""

from __future__ import annotations

import pandas as pd
import pytest

from churn_predictor.data import preprocessing
from churn_predictor.data.preprocessing import Preprocessor

pytestmark = pytest.mark.slow

# Heavy, optional deps — skip cleanly if they aren't installed.
pytest.importorskip("optuna")
pytest.importorskip("xgboost")
pytest.importorskip("lightgbm")
pytest.importorskip("catboost")


def test_tune_selects_best_algorithm(synthetic_customers: pd.DataFrame) -> None:
    from churn_predictor.models.tuning import tune

    data = preprocessing.split(synthetic_customers)
    pre = Preprocessor().fit(data.X_train)
    x_train = pre.transform(data.X_train)

    result = tune(
        x_train,
        data.y_train,
        algorithms=("xgboost", "lightgbm", "catboost"),
        n_trials=1,
    )

    assert result.algorithm in {"xgboost", "lightgbm", "catboost"}
    assert 0.0 <= result.best_score <= 1.0
    # Every candidate algorithm was evaluated.
    assert set(result.study_per_algo) == {"xgboost", "lightgbm", "catboost"}
    assert result.best_params  # non-empty hyperparameters
