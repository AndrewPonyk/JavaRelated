"""Risk-limit tests. Target: 100% coverage — these protect real money."""

from __future__ import annotations

from decimal import Decimal

import pytest
from risk_engine.limits import Decision, RiskEngine, RiskLimits

from trading_common.models.market import Position, Side, Signal

LIMITS = RiskLimits(
    max_position_qty=1000,
    max_order_notional=Decimal("250000"),
    max_daily_loss=Decimal("50000"),
)


def _sig(side: Side = Side.BUY, qty: int = 100) -> Signal:
    return Signal(strategy_id="s", symbol="AAPL", side=side, quantity=qty)


def _flat() -> Position:
    return Position(symbol="AAPL")


def test_approves_within_all_limits() -> None:
    res = RiskEngine(LIMITS).check(
        _sig(),
        reference_price=Decimal("100"),
        current_position=_flat(),
        realized_daily_pnl=Decimal("0"),
    )
    assert res.approved


def test_kill_switch_rejects_everything() -> None:
    limits = RiskLimits(1000, Decimal("250000"), Decimal("50000"), kill_switch=True)
    res = RiskEngine(limits).check(
        _sig(),
        reference_price=Decimal("1"),
        current_position=_flat(),
        realized_daily_pnl=Decimal("0"),
    )
    assert res.decision is Decision.REJECTED
    assert "kill-switch" in res.reason


def test_daily_loss_limit_rejects() -> None:
    res = RiskEngine(LIMITS).check(
        _sig(),
        reference_price=Decimal("100"),
        current_position=_flat(),
        realized_daily_pnl=Decimal("-50000"),
    )
    assert res.decision is Decision.REJECTED
    assert "daily loss" in res.reason


def test_notional_cap_rejects() -> None:
    res = RiskEngine(LIMITS).check(
        _sig(qty=100),
        reference_price=Decimal("3000"),  # 300k > 250k
        current_position=_flat(),
        realized_daily_pnl=Decimal("0"),
    )
    assert res.decision is Decision.REJECTED
    assert "notional" in res.reason


def test_position_cap_rejects_on_projection() -> None:
    pos = Position(symbol="AAPL", quantity=950)
    res = RiskEngine(LIMITS).check(
        _sig(side=Side.BUY, qty=100),  # 950 + 100 = 1050 > 1000
        reference_price=Decimal("100"),
        current_position=pos,
        realized_daily_pnl=Decimal("0"),
    )
    assert res.decision is Decision.REJECTED
    assert "position" in res.reason


def test_sell_reduces_position_and_passes() -> None:
    pos = Position(symbol="AAPL", quantity=950)
    res = RiskEngine(LIMITS).check(
        _sig(side=Side.SELL, qty=100),  # 950 - 100 = 850, within cap
        reference_price=Decimal("100"),
        current_position=pos,
        realized_daily_pnl=Decimal("0"),
    )
    assert res.approved


@pytest.mark.parametrize("qty", [1, 1000])
def test_boundary_quantities(qty: int) -> None:
    res = RiskEngine(LIMITS).check(
        _sig(qty=qty),
        reference_price=Decimal("100"),
        current_position=_flat(),
        realized_daily_pnl=Decimal("0"),
    )
    assert res.approved
