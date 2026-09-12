"""Unit tests for the synthetic data generator."""

from __future__ import annotations

from churn_predictor.data.loader import EXPECTED_COLUMNS
from churn_predictor.data.synthetic import generate_synthetic_customers


def test_generate_shape_and_columns() -> None:
    df = generate_synthetic_customers(250, seed=7)
    assert len(df) == 250
    assert set(EXPECTED_COLUMNS).issubset(df.columns)


def test_generate_target_is_binary() -> None:
    df = generate_synthetic_customers(300)
    assert set(df["is_churned"].unique()).issubset({0, 1})
    # A learnable signal should produce a non-degenerate target.
    assert 0.0 < df["is_churned"].mean() < 1.0


def test_generate_is_deterministic() -> None:
    a = generate_synthetic_customers(100, seed=1)
    b = generate_synthetic_customers(100, seed=1)
    assert a.equals(b)
    c = generate_synthetic_customers(100, seed=2)
    assert not a.equals(c)
