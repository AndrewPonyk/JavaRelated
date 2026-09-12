"""Regression: coefficient recovery on synthetic data, diagnostics, VIF, injection guard."""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from app.core.errors import AnalysisError

statsmodels = pytest.importorskip("statsmodels")

from app.stats import regression as reg  # noqa: E402


@pytest.fixture()
def linear_df(rng: np.random.Generator) -> pd.DataFrame:
    n = 300
    x1 = rng.normal(0, 1, n)
    x2 = rng.normal(5, 2, n)
    y = 2.0 + 3.0 * x1 - 1.5 * x2 + rng.normal(0, 0.5, n)
    return pd.DataFrame({"y": y, "x1": x1, "x2": x2})


def test_ols_recovers_known_coefficients(linear_df: pd.DataFrame) -> None:
    result = reg.fit_ols(linear_df, "y", ["x1", "x2"])
    coefs = dict(zip(result.coefficients["term"], result.coefficients["coef"], strict=False))
    assert coefs["Intercept"] == pytest.approx(2.0, abs=0.3)
    assert coefs["x1"] == pytest.approx(3.0, abs=0.15)
    assert coefs["x2"] == pytest.approx(-1.5, abs=0.1)
    assert result.r_squared is not None and result.r_squared > 0.95
    assert result.n_obs == 300


def test_ols_carries_diagnostics_and_residuals(linear_df: pd.DataFrame) -> None:
    result = reg.fit_ols(linear_df, "y", ["x1", "x2"])
    assert "durbin_watson" in result.diagnostics
    assert 1.0 < result.diagnostics["durbin_watson"] < 3.0  # iid noise
    assert "jarque_bera_p" in result.diagnostics
    assert "breusch_pagan_p" in result.diagnostics
    assert result.residuals.size == result.n_obs
    assert result.fitted.size == result.n_obs


def test_vif_flags_collinear_features(rng: np.random.Generator) -> None:
    n = 200
    x1 = rng.normal(0, 1, n)
    near_copy = x1 + rng.normal(0, 0.01, n)  # almost perfectly collinear
    y = x1 + rng.normal(0, 1, n)
    df = pd.DataFrame({"y": y, "x1": x1, "x2": near_copy})
    result = reg.fit_ols(df, "y", ["x1", "x2"])
    assert result.vif is not None
    assert float(result.vif["vif"].max()) > 100.0


def test_vif_none_with_single_feature(linear_df: pd.DataFrame) -> None:
    result = reg.fit_ols(linear_df, "y", ["x1"])
    assert result.vif is None


def test_logistic_on_separable_signal(rng: np.random.Generator) -> None:
    n = 400
    x = rng.normal(0, 1, n)
    prob = 1.0 / (1.0 + np.exp(-(0.5 + 2.0 * x)))
    y = rng.binomial(1, prob)
    df = pd.DataFrame({"y": y, "x": x})
    result = reg.fit_logistic(df, "y", ["x"])
    coefs = dict(zip(result.coefficients["term"], result.coefficients["coef"], strict=False))
    assert coefs["x"] == pytest.approx(2.0, abs=0.6)
    assert result.pseudo_r_squared is not None and result.pseudo_r_squared > 0.15
    assert result.fitted.min() >= 0.0 and result.fitted.max() <= 1.0  # probabilities


def test_logistic_rejects_non_binary_outcome(linear_df: pd.DataFrame) -> None:
    with pytest.raises(AnalysisError):
        reg.fit_logistic(linear_df, "y", ["x1"])


def test_quoted_column_names_are_supported(rng: np.random.Generator) -> None:
    df = pd.DataFrame({"the outcome": rng.normal(0, 1, 50), "a feature": rng.normal(0, 1, 50)})
    result = reg.fit_ols(df, "the outcome", ["a feature"])
    assert 'Q("the outcome")' in result.formula


def test_injection_like_column_names_are_rejected(rng: np.random.Generator) -> None:
    hostile = 'x"); import os #'
    df = pd.DataFrame({"y": rng.normal(0, 1, 50), hostile: rng.normal(0, 1, 50)})
    with pytest.raises(AnalysisError):
        reg.fit_ols(df, "y", [hostile])


def test_qq_points_shapes(linear_df: pd.DataFrame) -> None:
    result = reg.fit_ols(linear_df, "y", ["x1", "x2"])
    theoretical, ordered = reg.qq_points(result.residuals)
    assert theoretical.shape == ordered.shape == (300,)
    assert np.all(np.diff(ordered) >= 0)  # ordered sample is sorted


def test_serialization_excludes_arrays(linear_df: pd.DataFrame) -> None:
    payload = reg.fit_ols(linear_df, "y", ["x1", "x2"]).to_json_dict()
    assert payload["model_kind"] == "ols"
    assert isinstance(payload["coefficients"], list)
    assert "fitted" not in payload and "residuals" not in payload
