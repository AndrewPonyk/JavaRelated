"""Tumbling-window aggregation for sub-second business metrics.

Event-time windows with a watermark: a window [start, start+window_ms) closes
when watermark = max_seen_ts - allowed_lateness passes its end. Events older
than an already-closed window are dropped AND counted (late_events_dropped) —
alarm on that counter before touching window width (TECH-NOTES §3.6 #7).

Pure logic, no I/O — keep it that way so it stays trivially testable.
"""

from __future__ import annotations

from dataclasses import dataclass, field

ORDER_PLACED = "order_placed"
CHECKOUT_FAILED = "checkout_failed"


@dataclass(frozen=True)
class Event:
    """Normalized envelope of events.orders.v1 messages."""

    event_id: str
    event_type: str
    ts_ms: int  # producer event-time, epoch millis
    amount: float = 0.0
    attrs: dict = field(default_factory=dict)


@dataclass(frozen=True)
class MetricPoint:
    metric: str
    value: float
    window_start_ms: int
    window_ms: int


@dataclass
class _WindowState:
    orders: int = 0
    revenue: float = 0.0
    failures: int = 0
    events: int = 0


class TumblingWindowAggregator:
    """Aggregates events into fixed windows and emits MetricPoints on close."""

    def __init__(self, window_ms: int = 500, allowed_lateness_ms: int = 1000) -> None:
        if window_ms <= 0:
            raise ValueError("window_ms must be positive")
        self.window_ms = window_ms
        self.allowed_lateness_ms = allowed_lateness_ms
        self.late_events_dropped = 0
        self._windows: dict[int, _WindowState] = {}
        self._max_ts_ms = -1

    def add(self, event: Event) -> list[MetricPoint]:
        """Ingest one event; return MetricPoints for any windows that closed."""
        self._max_ts_ms = max(self._max_ts_ms, event.ts_ms)
        watermark = self._max_ts_ms - self.allowed_lateness_ms
        window_start = (event.ts_ms // self.window_ms) * self.window_ms

        if window_start + self.window_ms <= watermark:
            # The event's window already closed — too late to amend an emitted value.
            self.late_events_dropped += 1
            return self._close_ready(watermark)

        state = self._windows.setdefault(window_start, _WindowState())
        state.events += 1
        if event.event_type == ORDER_PLACED:
            state.orders += 1
            state.revenue += event.amount
        elif event.event_type == CHECKOUT_FAILED:
            state.failures += 1

        return self._close_ready(watermark)

    def flush(self) -> list[MetricPoint]:
        """Force-emit every open window (graceful shutdown path)."""
        out: list[MetricPoint] = []
        for start in sorted(self._windows):
            out.extend(self._emit(start, self._windows[start]))
        self._windows.clear()
        return out

    def _close_ready(self, watermark: int) -> list[MetricPoint]:
        ready = sorted(s for s in self._windows if s + self.window_ms <= watermark)
        out: list[MetricPoint] = []
        for start in ready:
            out.extend(self._emit(start, self._windows.pop(start)))
        return out

    def _emit(self, start: int, state: _WindowState) -> list[MetricPoint]:
        seconds = self.window_ms / 1000.0
        avg_order_value = state.revenue / state.orders if state.orders else 0.0
        attempts = state.orders + state.failures
        error_rate = state.failures / attempts if attempts else 0.0
        return [
            MetricPoint("orders_per_second", state.orders / seconds, start, self.window_ms),
            MetricPoint("revenue_per_second", state.revenue / seconds, start, self.window_ms),
            MetricPoint("avg_order_value", avg_order_value, start, self.window_ms),
            MetricPoint("checkout_error_rate", error_rate, start, self.window_ms),
        ]
