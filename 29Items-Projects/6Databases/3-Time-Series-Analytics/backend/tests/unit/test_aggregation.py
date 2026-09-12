"""Window math must be exact — it defines Redis key identity."""

from datetime import UTC, datetime

from app.services.aggregation import window_key, window_start


def test_window_start_floors_to_minute_boundary():
    ts = datetime(2026, 7, 2, 12, 34, 56, tzinfo=UTC)
    assert window_start(ts, 60) == datetime(2026, 7, 2, 12, 34, 0, tzinfo=UTC)


def test_window_start_is_identity_on_boundary():
    ts = datetime(2026, 7, 2, 12, 34, 0, tzinfo=UTC)
    assert window_start(ts, 60) == ts


def test_window_start_supports_other_window_sizes():
    ts = datetime(2026, 7, 2, 12, 34, 56, tzinfo=UTC)
    assert window_start(ts, 300) == datetime(2026, 7, 2, 12, 30, 0, tzinfo=UTC)


def test_points_in_same_window_share_a_key():
    a = datetime(2026, 7, 2, 12, 34, 1, tzinfo=UTC)
    b = datetime(2026, 7, 2, 12, 34, 59, tzinfo=UTC)
    assert window_key("dev-1", "temperature", a, 60) == window_key("dev-1", "temperature", b, 60)


def test_adjacent_windows_get_distinct_keys():
    a = datetime(2026, 7, 2, 12, 34, 59, tzinfo=UTC)
    b = datetime(2026, 7, 2, 12, 35, 0, tzinfo=UTC)
    assert window_key("dev-1", "temperature", a, 60) != window_key("dev-1", "temperature", b, 60)


def test_key_isolates_device_and_metric():
    ts = datetime(2026, 7, 2, 12, 34, 30, tzinfo=UTC)
    keys = {
        window_key("dev-1", "temperature", ts, 60),
        window_key("dev-2", "temperature", ts, 60),
        window_key("dev-1", "humidity", ts, 60),
    }
    assert len(keys) == 3
