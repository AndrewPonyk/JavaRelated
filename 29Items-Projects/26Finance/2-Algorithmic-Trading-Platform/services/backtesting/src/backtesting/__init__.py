"""backtesting — event-driven backtester sharing the live Strategy code path.

``SimpleBacktester`` is the default (pure-Python, always available). ``run_backtest``
wraps Zipline as a heavy optional alternative and is imported lazily.
"""

from backtesting.metrics import BacktestResult, compute_metrics  # noqa: F401
from backtesting.simple import SimpleBacktester  # noqa: F401

__all__ = ["BacktestResult", "SimpleBacktester", "compute_metrics", "run_backtest"]


def __getattr__(name: str):
    # Lazy import so importing `backtesting` doesn't require Zipline.
    if name == "run_backtest":
        from backtesting.runner import run_backtest

        return run_backtest
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
