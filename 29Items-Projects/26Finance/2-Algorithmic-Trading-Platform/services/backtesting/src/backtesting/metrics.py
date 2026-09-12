"""Backtest performance metrics."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

TRADING_DAYS = 252


@dataclass
class BacktestResult:
    total_return: float
    sharpe: float
    max_drawdown: float
    n_trades: int
    final_equity: float
    n_bars: int


def compute_metrics(equity_curve: list[float], n_trades: int, *, periods_per_year: int = TRADING_DAYS) -> BacktestResult:
    """Compute return/Sharpe/drawdown from an equity curve."""
    eq = np.asarray(equity_curve, dtype=np.float64)
    if eq.size < 2:
        start = float(eq[0]) if eq.size else 0.0
        return BacktestResult(0.0, 0.0, 0.0, n_trades, start, int(eq.size))

    total_return = float(eq[-1] / eq[0] - 1.0)

    rets = np.diff(eq) / eq[:-1]
    std = float(rets.std(ddof=1)) if rets.size > 1 else 0.0
    sharpe = float(rets.mean() / std * np.sqrt(periods_per_year)) if std > 0 else 0.0

    running_max = np.maximum.accumulate(eq)
    drawdowns = (eq - running_max) / running_max
    max_drawdown = float(drawdowns.min())  # most negative

    return BacktestResult(
        total_return=total_return,
        sharpe=sharpe,
        max_drawdown=max_drawdown,
        n_trades=n_trades,
        final_equity=float(eq[-1]),
        n_bars=int(eq.size),
    )
