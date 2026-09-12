"""Unit tests for the data loader (CSV path + schema validation)."""

from __future__ import annotations

import pandas as pd
import pytest

from churn_predictor import DataLoadError
from churn_predictor.data import loader


def test_load_from_csv_roundtrip(tmp_path, synthetic_customers: pd.DataFrame) -> None:
    csv = tmp_path / "customers.csv"
    synthetic_customers.to_csv(csv, index=False)

    df = loader.load_customers(source="csv", path=csv)
    assert len(df) == len(synthetic_customers)
    assert set(loader.EXPECTED_COLUMNS).issubset(df.columns)


def test_load_csv_limit(tmp_path, synthetic_customers: pd.DataFrame) -> None:
    csv = tmp_path / "customers.csv"
    synthetic_customers.to_csv(csv, index=False)
    df = loader.load_customers(source="csv", path=csv, limit=10)
    assert len(df) == 10


def test_validate_schema_missing_column(synthetic_customers: pd.DataFrame) -> None:
    bad = synthetic_customers.drop(columns=["monthly_charges"])
    with pytest.raises(DataLoadError):
        loader.validate_schema(bad)


def test_validate_schema_optional_target(synthetic_customers: pd.DataFrame) -> None:
    no_target = synthetic_customers.drop(columns=["is_churned"])
    loader.validate_schema(no_target, require_target=False)  # should not raise


def test_unknown_source_raises() -> None:
    with pytest.raises(DataLoadError):
        loader.load_customers(source="ftp")


def test_csv_missing_path_raises() -> None:
    with pytest.raises(DataLoadError):
        loader.load_customers(source="csv")
