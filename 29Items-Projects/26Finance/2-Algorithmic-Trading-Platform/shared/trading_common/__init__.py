"""trading_common — shared domain models, indicators, and utilities.

This package is the single source of truth for signal/indicator math so that a
backtest (Zipline) and a live strategy compute identical values (backtest/live parity).
"""

__version__ = "0.1.0"

from trading_common.models.market import (
    Bar,
    Fill,
    Order,
    OrderStatus,
    OrderType,
    Position,
    RiskDecision,
    Side,
    Signal,
    Tick,
)

__all__ = [
    "Bar",
    "Fill",
    "Order",
    "OrderStatus",
    "OrderType",
    "Position",
    "RiskDecision",
    "Side",
    "Signal",
    "Tick",
    "__version__",
]
