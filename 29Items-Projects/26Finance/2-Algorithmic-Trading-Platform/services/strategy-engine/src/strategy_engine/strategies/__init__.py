"""Concrete strategies. Each subclasses ``base.Strategy`` (the parity contract)."""

from strategy_engine.strategies.base import Strategy
from strategy_engine.strategies.mean_reversion import BollingerReversionStrategy
from strategy_engine.strategies.momentum import EmaCrossoverStrategy

__all__ = ["BollingerReversionStrategy", "EmaCrossoverStrategy", "Strategy"]
