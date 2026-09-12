"""Pre-trade risk checks — the gate every order passes before reaching the broker.

This is on the order critical path: it must be fast and **fail closed**. If any
check cannot be evaluated (missing data, kill-switch engaged), the order is REJECTED
(ARCHITECTURE §2.6).
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from trading_common.models.market import Position, Signal


class Decision(str, Enum):
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


@dataclass(frozen=True)
class RiskLimits:
    """Authoritative per-account limits (loaded from config/risk-engine)."""

    max_position_qty: int
    max_order_notional: Decimal
    max_daily_loss: Decimal
    kill_switch: bool = False


@dataclass(frozen=True)
class RiskResult:
    decision: Decision
    reason: str = ""

    @property
    def approved(self) -> bool:
        return self.decision is Decision.APPROVED


class RiskEngine:
    """Synchronous pre-trade checker. Reads current state from Redis in production."""

    def __init__(self, limits: RiskLimits) -> None:
        self._limits = limits

    def check(
        self,
        signal: Signal,
        *,
        reference_price: Decimal,
        current_position: Position,
        realized_daily_pnl: Decimal,
    ) -> RiskResult:
        """Evaluate a signal against all limits. Order of checks = fail fast."""

        # 1. Global kill-switch — highest priority, fail closed.
        if self._limits.kill_switch:
            return RiskResult(Decision.REJECTED, "kill-switch engaged")

        # 2. Daily loss cap.
        if realized_daily_pnl <= -self._limits.max_daily_loss:
            return RiskResult(Decision.REJECTED, "daily loss limit reached")

        # 3. Order notional cap.
        notional = reference_price * Decimal(signal.quantity)
        if notional > self._limits.max_order_notional:
            return RiskResult(
                Decision.REJECTED,
                f"order notional {notional} exceeds cap {self._limits.max_order_notional}",
            )

        # 4. Resulting absolute position cap.
        delta = signal.quantity if signal.side.value == "BUY" else -signal.quantity
        projected = abs(current_position.quantity + delta)
        if projected > self._limits.max_position_qty:
            return RiskResult(
                Decision.REJECTED,
                f"projected position {projected} exceeds cap {self._limits.max_position_qty}",
            )

        return RiskResult(Decision.APPROVED)


# NOTE: RiskEngine is wired onto the bus by ``risk_engine.service.RiskService``,
# which consumes ``signals`` and produces ``orders`` / ``risk.decisions`` while
# reading live positions from the PositionStore (Redis in production).
