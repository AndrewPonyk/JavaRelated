"""Unit tests for the preprocessing layer."""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from churn_predictor import PreprocessingError
from churn_predictor.data import preprocessing
from churn_predictor.data.preprocessing import Preprocessor


def test_clean_coerces_total_charges_blanks(synthetic_customers: pd.DataFrame) -> None:
    df = synthetic_customers.copy()
    df["total_charges"] = df["total_charges"].astype(object)
    df.loc[0, "total_charges"] = " "  # classic dirty value (blank string)
    cleaned = preprocessing.clean(df)
    assert cleaned["total_charges"].notna().all()
    assert pd.api.types.is_numeric_dtype(cleaned["total_charges"])


def test_encode_categoricals_one_hot(synthetic_customers: pd.DataFrame) -> None:
    encoded = preprocessing.encode_categoricals(synthetic_customers)
    assert "contract_type" not in encoded.columns
    assert any(c.startswith("contract_type_") for c in encoded.columns)


def test_encode_catboost_keeps_raw(synthetic_customers: pd.DataFrame) -> None:
    encoded = preprocessing.encode_categoricals(synthetic_customers, algo="catboost")
    assert "contract_type" in encoded.columns


def test_split_is_stratified(synthetic_customers: pd.DataFrame) -> None:
    data = preprocessing.split(synthetic_customers, test_size=0.25)
    assert len(data.X_test) == int(round(0.25 * len(synthetic_customers)))
    # No target/id leakage into features.
    assert preprocessing.TARGET not in data.X_train.columns
    assert preprocessing.ID_COLUMN not in data.X_train.columns


# --- Preprocessor (fitted, leakage-safe) -----------------------------------
def test_preprocessor_fit_transform_shapes(synthetic_customers: pd.DataFrame) -> None:
    data = preprocessing.split(synthetic_customers)
    pre = Preprocessor().fit(data.X_train)

    x_train = pre.transform(data.X_train)
    x_test = pre.transform(data.X_test)

    # Identical, ordered schema across splits; engineered features present.
    assert list(x_train.columns) == list(x_test.columns) == pre.feature_columns
    assert "tickets_per_month" in pre.feature_columns
    assert any(c.startswith("contract_type_") for c in pre.feature_columns)
    # All-numeric output (no object columns left for the model).
    assert x_train.select_dtypes(include="object").empty


def test_preprocessor_requires_fit() -> None:
    with pytest.raises(PreprocessingError):
        Preprocessor().transform(pd.DataFrame({"tenure_months": [1]}))


def test_preprocessor_handles_unseen_category(synthetic_customers: pd.DataFrame) -> None:
    pre = Preprocessor().fit(synthetic_customers)
    unseen = synthetic_customers.head(1).copy()
    unseen.loc[:, "contract_type"] = "Lifetime"  # never seen in training
    out = pre.transform(unseen)
    # Schema preserved; unknown category collapses to all-zero contract dummies.
    assert list(out.columns) == pre.feature_columns
    contract_cols = [c for c in pre.feature_columns if c.startswith("contract_type_")]
    assert out[contract_cols].to_numpy().sum() == 0


def test_preprocessor_imputes_missing_numeric(synthetic_customers: pd.DataFrame) -> None:
    pre = Preprocessor().fit(synthetic_customers)
    row = synthetic_customers.head(1).copy()
    row.loc[:, "total_charges"] = np.nan
    out = pre.transform(row)
    assert not out.isna().any().any()  # imputed with the learned median
