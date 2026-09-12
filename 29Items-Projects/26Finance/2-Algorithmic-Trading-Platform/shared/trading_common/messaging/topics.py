"""Canonical Kafka topic names (ARCHITECTURE Appendix A)."""

from __future__ import annotations

from typing import Final

MARKET_TICKS: Final = "market.ticks"
MARKET_BARS: Final = "market.bars"
PREDICTIONS: Final = "predictions"
SIGNALS: Final = "signals"
ORDERS: Final = "orders"
FILLS: Final = "fills"
RISK_DECISIONS: Final = "risk.decisions"
COMMANDS: Final = "commands"

ALL_TOPICS: Final = (
    MARKET_TICKS,
    MARKET_BARS,
    PREDICTIONS,
    SIGNALS,
    ORDERS,
    FILLS,
    RISK_DECISIONS,
    COMMANDS,
)
