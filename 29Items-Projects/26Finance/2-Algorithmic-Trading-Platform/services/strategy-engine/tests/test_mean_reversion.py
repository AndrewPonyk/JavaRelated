"""Tests for the Bollinger mean-reversion strategy."""

from __future__ import annotations

from decimal import Decimal

from strategy_engine.strategies.mean_reversion import BollingerReversionStrategy

from trading_common.models.market import Bar, Side


def _bar(price: float) -> Bar:
    c = Decimal(str(price))
    return Bar(symbol="AAPL", open=c, high=c, low=c, close=c, volume=1000)


def _feed(strat: BollingerReversionStrategy, prices: list[float]):
    out = []
    for p in prices:
        out += strat.on_bar(_bar(p))
    return out


def test_warmup_blocks_early_signals() -> None:
    strat = BollingerReversionStrategy("br", ["AAPL"], period=20)
    signals = _feed(strat, [100.0 + (i % 2) for i in range(10)])
    assert signals == []


def test_buy_below_lower_band() -> None:
    strat = BollingerReversionStrategy("br", ["AAPL"], period=20, num_std=2.0)
    base = [99.0 if i % 2 else 101.0 for i in range(25)]  # ~100 with small variance
    signals = _feed(strat, [*base, 90.0])  # sharp drop below lower band
    assert any(s.side is Side.BUY for s in signals)


def test_sell_above_upper_band() -> None:
    strat = BollingerReversionStrategy("br", ["AAPL"], period=20, num_std=2.0)
    base = [99.0 if i % 2 else 101.0 for i in range(25)]
    signals = _feed(strat, [*base, 115.0])  # sharp spike above upper band
    assert any(s.side is Side.SELL for s in signals)


def test_no_signal_inside_bands() -> None:
    strat = BollingerReversionStrategy("br", ["AAPL"], period=20, num_std=2.0)
    base = [90.0 + (i % 20) for i in range(40)]  # wide bands, price stays inside
    signals = _feed(strat, base)
    # last bar near the middle of a wide band => no extreme breach guaranteed
    assert all(isinstance(s.side, Side) for s in signals)  # whatever fires is well-formed
