"""Value-at-Risk / Expected Shortfall on Pandas return series and portfolios.

Convention: `returns` are simple period returns; VaR/ES are reported as
POSITIVE loss fractions at the given confidence (0.99 → 1-day 99% VaR if
the input is daily returns).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

import numpy as np
import pandas as pd

from quantfinlib._compat import backend
from quantfinlib.utils.validation import InvalidInputError

Method = Literal["historical", "parametric"]


@dataclass(frozen=True)
class VarReport:
    var: float
    expected_shortfall: float
    confidence: float
    method: Method
    n_observations: int


def _to_clean_array(returns: pd.Series | np.ndarray) -> np.ndarray:
    arr = np.asarray(returns, dtype=np.float64).ravel()
    arr = arr[np.isfinite(arr)]  # drop NaN gaps (weekends/missing quotes)
    if arr.size < 2:
        raise InvalidInputError("need at least 2 finite return observations")
    return np.ascontiguousarray(arr)


def value_at_risk(
    returns: pd.Series | np.ndarray, confidence: float = 0.99, method: Method = "historical"
) -> VarReport:
    """Single-series VaR/ES.

    >>> import numpy as np
    >>> r = np.random.default_rng(0).normal(0, 0.01, 1000)
    >>> report = value_at_risk(r, confidence=0.99)
    >>> 0.01 < report.var < 0.05
    True
    """
    if not 0.0 < confidence < 1.0:
        raise InvalidInputError(f"confidence must be in (0, 1), got {confidence}")
    arr = _to_clean_array(returns)
    if method == "historical":
        raw = backend.historical_var(arr, confidence)
    elif method == "parametric":
        raw = backend.parametric_var(arr, confidence)
    else:
        raise InvalidInputError(f"method must be 'historical' or 'parametric', got {method!r}")
    return VarReport(
        var=raw["var"],
        expected_shortfall=raw["expected_shortfall"],
        confidence=confidence,
        method=method,
        n_observations=int(arr.size),
    )


def expected_shortfall(
    returns: pd.Series | np.ndarray, confidence: float = 0.99, method: Method = "historical"
) -> float:
    return value_at_risk(returns, confidence, method).expected_shortfall


def portfolio_var(
    returns: pd.DataFrame,
    weights: pd.Series | np.ndarray,
    confidence: float = 0.99,
    method: Method = "historical",
) -> VarReport:
    """Portfolio VaR from an assets×time return frame and weights.

    Aggregates each period's weighted return, then applies single-series
    VaR. For the historical method this captures empirical correlations for
    free; for the parametric method it is mathematically identical to the
    w'Σw variance-covariance form (the sample variance of the aggregated
    series *is* w'Σw).
    """
    w = np.asarray(weights, dtype=np.float64).ravel()
    if w.size != returns.shape[1]:
        raise InvalidInputError(f"weights length {w.size} != number of assets {returns.shape[1]}")
    if not np.isclose(w.sum(), 1.0):
        raise InvalidInputError(f"weights must sum to 1.0, got {w.sum():.6f}")
    portfolio_returns = returns.to_numpy(dtype=np.float64) @ w
    return value_at_risk(portfolio_returns, confidence, method)
