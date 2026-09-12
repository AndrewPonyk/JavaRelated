import numpy as np
import pandas as pd
import pytest

from quantfinlib import _pure
from quantfinlib.risk import portfolio_var, value_at_risk
from quantfinlib.utils.validation import InvalidInputError


def test_historical_var_on_known_distribution(daily_returns):
    report = value_at_risk(daily_returns, confidence=0.99, method="historical")
    # N(0.02%, 1%) daily → 99% VaR ≈ 2.33% - 0.02%; generous statistical band.
    assert 0.015 < report.var < 0.035
    assert report.expected_shortfall >= report.var  # ES dominates VaR by definition


def test_historical_var_matches_numpy_quantile(daily_returns):
    report = value_at_risk(daily_returns, confidence=0.99, method="historical")
    assert report.var == pytest.approx(-float(np.quantile(daily_returns, 0.01)), abs=1e-12)


def test_backend_parity_with_pure_python(daily_returns):
    for method, fn in (("historical", _pure.historical_var), ("parametric", _pure.parametric_var)):
        report = value_at_risk(daily_returns, 0.975, method)
        pure = fn(daily_returns, 0.975)
        assert report.var == pytest.approx(pure["var"], abs=1e-9)
        assert report.expected_shortfall == pytest.approx(pure["expected_shortfall"], abs=1e-9)


def test_parametric_close_to_historical_for_normal_data(daily_returns):
    hist = value_at_risk(daily_returns, 0.99, "historical")
    para = value_at_risk(daily_returns, 0.99, "parametric")
    assert para.var == pytest.approx(hist.var, rel=0.25)


def test_nan_gaps_are_dropped(daily_returns):
    with_gaps = daily_returns.copy()
    with_gaps[::50] = np.nan
    report = value_at_risk(pd.Series(with_gaps), 0.99)
    assert report.n_observations == np.isfinite(with_gaps).sum()


def test_portfolio_var_weights_must_sum_to_one(daily_returns):
    frame = pd.DataFrame({"a": daily_returns, "b": daily_returns[::-1]})
    with pytest.raises(InvalidInputError):
        portfolio_var(frame, weights=[0.7, 0.7])


def test_diversification_lowers_var(rng):
    # Two anti-correlated assets: the 50/50 book must be less risky than either leg.
    a = rng.normal(0, 0.02, 2000)
    b = -0.8 * a + rng.normal(0, 0.005, 2000)
    frame = pd.DataFrame({"a": a, "b": b})
    port = portfolio_var(frame, weights=[0.5, 0.5], confidence=0.99)
    single = value_at_risk(a, confidence=0.99)
    assert port.var < single.var


@pytest.mark.parametrize("confidence", [0.0, 1.0, -0.5, 1.5])
def test_confidence_bounds(daily_returns, confidence):
    with pytest.raises(InvalidInputError):
        value_at_risk(daily_returns, confidence=confidence)
