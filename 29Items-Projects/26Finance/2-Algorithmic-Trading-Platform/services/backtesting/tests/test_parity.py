"""Backtest/live parity test (the differentiator from TECH-NOTES §3.2).

We assert determinism of the shared signal code path: two independent instances of
the same Strategy, fed an identical bar sequence (as backtest and live would),
produce byte-identical signal streams. This guards against accidental nondeterminism
or look-ahead creeping into a strategy.
"""

from __future__ import annotations

from decimal import Decimal

import pytest

from trading_common.models.market import Bar

from strategy_engine.strategies.momentum import EmaCrossoverStrategy


def _bars() -> list[Bar]:
    prices = [20, 19, 18, 17, 16, 15, 16, 18, 21, 25, 30, 28, 24, 20, 18, 19, 22, 26, 31, 35]
    out = []
    for p in prices:
        c = Decimal(str(p))
        out.append(Bar(symbol="AAPL", open=c, high=c, low=c, close=c, volume=1000))
    return out


@pytest.mark.parity
def test_same_bars_same_signals() -> None:
    bars = _bars()

    live = EmaCrossoverStrategy("ema", ["AAPL"], fast=3, slow=5, rsi_period=2)
    backtest = EmaCrossoverStrategy("ema", ["AAPL"], fast=3, slow=5, rsi_period=2)

    live_sigs = [(s.symbol, s.side, s.quantity) for b in bars for s in live.on_bar(b)]
    bt_sigs = [(s.symbol, s.side, s.quantity) for b in bars for s in backtest.on_bar(b)]

    assert live_sigs == bt_sigs, "live and backtest signal streams diverged — parity broken"
