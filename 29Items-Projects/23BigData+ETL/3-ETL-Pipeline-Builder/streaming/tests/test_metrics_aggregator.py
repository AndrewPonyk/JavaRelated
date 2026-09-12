from processor.metrics_aggregator import Event, TumblingWindowAggregator


def make(ts_ms: int, amount: float = 10.0, event_type: str = "order_placed") -> Event:
    return Event(
        event_id=f"e{ts_ms}-{event_type}", event_type=event_type, ts_ms=ts_ms, amount=amount
    )


def test_window_closes_when_watermark_passes_it():
    agg = TumblingWindowAggregator(window_ms=500, allowed_lateness_ms=0)

    assert agg.add(make(0, amount=10.0)) == []
    assert agg.add(make(400, amount=20.0)) == []

    emitted = agg.add(make(1000))  # watermark → 1000, closes [0, 500)
    by_name = {p.metric: p.value for p in emitted}

    assert by_name["orders_per_second"] == 4.0  # 2 orders / 0.5 s
    assert by_name["revenue_per_second"] == 60.0  # 30.0 / 0.5 s
    assert by_name["avg_order_value"] == 15.0
    assert by_name["checkout_error_rate"] == 0.0
    assert all(p.window_start_ms == 0 and p.window_ms == 500 for p in emitted)


def test_late_events_are_dropped_and_counted():
    agg = TumblingWindowAggregator(window_ms=500, allowed_lateness_ms=0)
    agg.add(make(0))
    agg.add(make(1200))  # closes [0, 500)

    out = agg.add(make(100))  # belongs to the already-closed window

    assert out == []
    assert agg.late_events_dropped == 1


def test_checkout_error_rate():
    agg = TumblingWindowAggregator(window_ms=500, allowed_lateness_ms=0)
    agg.add(make(0, amount=10.0))
    agg.add(make(100, event_type="checkout_failed", amount=0.0))

    out = agg.add(make(2000))
    by_name = {p.metric: p.value for p in out}

    assert by_name["checkout_error_rate"] == 0.5
    assert by_name["orders_per_second"] == 2.0  # 1 order / 0.5 s


def test_flush_emits_all_open_windows():
    agg = TumblingWindowAggregator(window_ms=500, allowed_lateness_ms=1000)
    agg.add(make(0, amount=10.0))
    agg.add(make(600, amount=30.0))

    points = agg.flush()

    starts = {p.window_start_ms for p in points}
    assert starts == {0, 500}
    avg_by_window = {p.window_start_ms: p.value for p in points if p.metric == "avg_order_value"}
    assert avg_by_window == {0: 10.0, 500: 30.0}
    assert agg.flush() == []  # idempotent once drained
