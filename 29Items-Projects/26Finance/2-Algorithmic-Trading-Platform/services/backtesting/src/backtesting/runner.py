"""Zipline-based backtest harness.

CRITICAL: this harness drives the SAME ``Strategy`` subclasses that run live in
``strategy-engine``. It adapts Zipline's per-bar callback to our ``on_bar`` contract,
so backtest and live share one signal code path (ARCHITECTURE §2.3.2).

Zipline pins older NumPy/Pandas — keep this service in its own venv/lockfile
(TECH-NOTES §3.6). Consider ``zipline-reloaded`` on modern Python.
"""

from __future__ import annotations

from decimal import Decimal

from trading_common.models.market import Bar, Signal

from backtesting.metrics import BacktestResult, compute_metrics

# NOTE: import lazily inside functions so the rest of the platform doesn't depend
# on Zipline's heavy/old dependency tree.


def _zipline_bar_to_domain(symbol: str, data) -> Bar:  # noqa: ANN001 - zipline BarData
    """Adapt a Zipline bar to our canonical :class:`Bar`."""
    price = Decimal(str(data.current(symbol, "price")))
    return Bar(
        symbol=str(symbol),
        open=price,
        high=price,
        low=price,
        close=price,
        volume=int(data.current(symbol, "volume") or 0),
    )


def run_backtest(strategy_factory, start: str, end: str, capital: float = 1_000_000.0):  # noqa: ANN001
    """Run a backtest for a strategy built by ``strategy_factory``.

    ``strategy_factory`` returns a fresh ``Strategy`` instance (so backtests are
    reproducible and isolated).
    """
    try:
        from zipline import run_algorithm  # type: ignore[import-untyped]
        from zipline.api import order, record, symbol  # noqa: F401
    except ImportError as exc:  # pragma: no cover
        raise ImportError(
            "Zipline is not installed in this environment. Use the backtesting venv."
        ) from exc

    strategy = strategy_factory()
    collected: list[Signal] = []

    def initialize(context) -> None:  # noqa: ANN001
        context.strategy = strategy

    def handle_data(context, data) -> None:  # noqa: ANN001
        for sym in context.strategy.symbols:
            bar = _zipline_bar_to_domain(sym, data)
            for sig in context.strategy.on_bar(bar):
                collected.append(sig)
                # TODO: translate Signal -> zipline order() with slippage/commission models.

    perf = run_algorithm(
        start=start, end=end, initialize=initialize, handle_data=handle_data,
        capital_base=capital, data_frequency="daily",
    )
    # Derive standard metrics from Zipline's portfolio-value series.
    equity_curve = [float(v) for v in perf["portfolio_value"].tolist()]
    return compute_metrics(equity_curve, n_trades=len(collected))
