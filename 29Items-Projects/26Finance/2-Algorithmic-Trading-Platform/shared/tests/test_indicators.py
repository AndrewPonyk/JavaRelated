"""Indicator tests: unit + property-based (hypothesis), per TECH-NOTES §3.2."""

from __future__ import annotations

import numpy as np
from hypothesis import given, settings
from hypothesis import strategies as st

from trading_common.indicators import atr, bollinger, ema, macd, rsi, sma


def test_sma_of_constant_is_constant() -> None:
    out = sma([5.0] * 10, 3)
    assert np.isnan(out[:2]).all()
    np.testing.assert_allclose(out[2:], 5.0)


def test_sma_known_values() -> None:
    out = sma([1, 2, 3, 4, 5], 2)
    np.testing.assert_allclose(out[1:], [1.5, 2.5, 3.5, 4.5])


def test_ema_warmup_then_tracks() -> None:
    out = ema([1, 2, 3, 4, 5, 6], 3)
    assert np.isnan(out[:2]).all()
    assert out[2] == 2.0  # seed = mean(1,2,3)
    assert out[-1] > out[2]  # rises with the uptrend


def test_rsi_bounds_and_extremes() -> None:
    up = rsi(list(range(1, 30)), 14)  # strictly rising => RSI = 100
    valid = up[~np.isnan(up)]
    assert valid.size > 0
    assert np.all(valid >= 0) and np.all(valid <= 100)
    assert valid[-1] == 100.0


def test_atr_is_nonnegative() -> None:
    n = 30
    high = np.linspace(10, 20, n) + 1
    low = np.linspace(10, 20, n) - 1
    close = np.linspace(10, 20, n)
    out = atr(high, low, close, 14)
    valid = out[~np.isnan(out)]
    assert valid.size > 0 and np.all(valid >= 0)


def test_macd_histogram_relationship() -> None:
    series = list(np.cumsum(np.random.default_rng(1).normal(size=80)) + 100)
    line, signal, hist = macd(series)
    mask = ~np.isnan(hist)
    np.testing.assert_allclose(hist[mask], (line - signal)[mask], atol=1e-9)


def test_bollinger_orders_bands() -> None:
    series = list(np.random.default_rng(2).normal(100, 5, size=50))
    upper, mid, lower = bollinger(series, 20, 2)
    mask = ~np.isnan(mid)
    assert np.all(upper[mask] >= mid[mask]) and np.all(mid[mask] >= lower[mask])


@settings(max_examples=50, deadline=None)
@given(
    st.lists(st.floats(min_value=1, max_value=1000, allow_nan=False), min_size=20, max_size=200),
    st.integers(min_value=2, max_value=15),
)
def test_sma_within_window_bounds(values: list[float], period: int) -> None:
    out = sma(values, period)
    arr = np.asarray(values)
    for i in range(period - 1, len(values)):
        window = arr[i - period + 1 : i + 1]
        assert window.min() - 1e-9 <= out[i] <= window.max() + 1e-9


@settings(max_examples=50, deadline=None)
@given(st.lists(st.floats(min_value=1, max_value=1000, allow_nan=False), min_size=20, max_size=200))
def test_rsi_always_in_range(values: list[float]) -> None:
    out = rsi(values, 14)
    valid = out[~np.isnan(out)]
    assert np.all((valid >= 0) & (valid <= 100))
