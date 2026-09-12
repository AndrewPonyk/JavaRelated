"""Technical indicators — the single shared code path (backtest/live parity).

Implemented in pure NumPy so the platform runs anywhere with no native
dependency. TA-Lib remains an optional drop-in *accelerator*: set
``TRADING_USE_TALIB=1`` and install the native ``libta-lib`` to route through it.
Either way strategies import from here only, so the dependency stays localized
(TECH-NOTES §3.6) and both backends share the leading-NaN warmup convention.
"""

from __future__ import annotations

import os

import numpy as np
import numpy.typing as npt

FloatArray = npt.NDArray[np.float64]

_USE_TALIB = os.getenv("TRADING_USE_TALIB") == "1"
if _USE_TALIB:  # pragma: no cover - exercised only when native TA-Lib is present
    import talib  # type: ignore[import-untyped]


def _as_array(values: npt.ArrayLike) -> FloatArray:
    arr = np.asarray(values, dtype=np.float64)
    if arr.ndim != 1:
        raise ValueError(f"expected 1-D series, got shape {arr.shape}")
    return arr


def sma(values: npt.ArrayLike, period: int) -> FloatArray:
    """Simple Moving Average (leading ``period-1`` values are NaN)."""
    if period <= 0:
        raise ValueError("period must be positive")
    a = _as_array(values)
    if _USE_TALIB:  # pragma: no cover
        return talib.SMA(a, timeperiod=period)
    out = np.full(a.shape, np.nan)
    if a.size >= period:
        csum = np.cumsum(np.insert(a, 0, 0.0))
        out[period - 1 :] = (csum[period:] - csum[:-period]) / period
    return out


def ema(values: npt.ArrayLike, period: int) -> FloatArray:
    """Exponential Moving Average, seeded with the SMA of the first ``period``."""
    if period <= 0:
        raise ValueError("period must be positive")
    a = _as_array(values)
    if _USE_TALIB:  # pragma: no cover
        return talib.EMA(a, timeperiod=period)
    out = np.full(a.shape, np.nan)
    if a.size >= period:
        k = 2.0 / (period + 1.0)
        prev = float(a[:period].mean())
        out[period - 1] = prev
        for i in range(period, a.size):
            prev = a[i] * k + prev * (1.0 - k)
            out[i] = prev
    return out


def rsi(values: npt.ArrayLike, period: int = 14) -> FloatArray:
    """Relative Strength Index (Wilder smoothing), range [0, 100]."""
    if period <= 0:
        raise ValueError("period must be positive")
    a = _as_array(values)
    if _USE_TALIB:  # pragma: no cover
        return talib.RSI(a, timeperiod=period)
    out = np.full(a.shape, np.nan)
    if a.size <= period:
        return out
    delta = np.diff(a)
    gain = np.where(delta > 0, delta, 0.0)
    loss = np.where(delta < 0, -delta, 0.0)

    def _rsi(avg_gain: float, avg_loss: float) -> float:
        if avg_loss == 0:
            return 100.0
        rs = avg_gain / avg_loss
        return 100.0 - 100.0 / (1.0 + rs)

    avg_gain = float(gain[:period].mean())
    avg_loss = float(loss[:period].mean())
    out[period] = _rsi(avg_gain, avg_loss)
    for i in range(period + 1, a.size):
        avg_gain = (avg_gain * (period - 1) + gain[i - 1]) / period
        avg_loss = (avg_loss * (period - 1) + loss[i - 1]) / period
        out[i] = _rsi(avg_gain, avg_loss)
    return out


def atr(
    high: npt.ArrayLike, low: npt.ArrayLike, close: npt.ArrayLike, period: int = 14
) -> FloatArray:
    """Average True Range (Wilder smoothing) — volatility for sizing/stops."""
    h, low_a, c = _as_array(high), _as_array(low), _as_array(close)
    if not (h.size == low_a.size == c.size):
        raise ValueError("high/low/close must be the same length")
    if _USE_TALIB:  # pragma: no cover
        return talib.ATR(h, low_a, c, timeperiod=period)
    n = c.size
    out = np.full(n, np.nan)
    if n <= period:
        return out
    tr = np.empty(n)
    tr[0] = h[0] - low_a[0]
    for i in range(1, n):
        tr[i] = max(h[i] - low_a[i], abs(h[i] - c[i - 1]), abs(low_a[i] - c[i - 1]))
    prev = float(tr[1 : period + 1].mean())
    out[period] = prev
    for i in range(period + 1, n):
        prev = (prev * (period - 1) + tr[i]) / period
        out[i] = prev
    return out


def macd(
    values: npt.ArrayLike, fast: int = 12, slow: int = 26, signal: int = 9
) -> tuple[FloatArray, FloatArray, FloatArray]:
    """MACD line, signal line, histogram."""
    a = _as_array(values)
    macd_line = ema(a, fast) - ema(a, slow)
    signal_line = np.full(a.shape, np.nan)
    valid = ~np.isnan(macd_line)
    if int(valid.sum()) >= signal:
        idx = np.where(valid)[0]
        signal_line[idx] = ema(macd_line[idx], signal)
    return macd_line, signal_line, macd_line - signal_line


def bollinger(
    values: npt.ArrayLike, period: int = 20, num_std: float = 2.0
) -> tuple[FloatArray, FloatArray, FloatArray]:
    """Bollinger Bands: (upper, middle, lower)."""
    a = _as_array(values)
    middle = sma(a, period)
    std = np.full(a.shape, np.nan)
    for i in range(period - 1, a.size):
        std[i] = a[i - period + 1 : i + 1].std(ddof=0)
    return middle + num_std * std, middle, middle - num_std * std
