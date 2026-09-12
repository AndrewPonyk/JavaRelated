"""Feature pipeline transformations — point-in-time windows, categorical
preference, and drift ratios (pure transforms, no I/O)."""

from __future__ import annotations

import datetime

import pytest

pytestmark = pytest.mark.spark

FEATURE_DATE = "2026-07-01"


def _order(customer: str, day: str, amount: float, currency: str = "EUR") -> dict:
    return {
        "customer_id": customer,
        "event_date": datetime.date.fromisoformat(day),
        "amount": amount,
        "currency": currency,
    }


def test_rolling_windows_and_lifetime(spark):
    from lakehouse.features.customer_order_features import compute_features

    orders = spark.createDataFrame(
        [
            _order("c-1", "2026-06-30", 10.0),  # inside 7d and 30d
            _order("c-1", "2026-06-10", 20.0),  # inside 30d only
            _order("c-1", "2026-01-01", 40.0),  # lifetime only
        ]
    )
    row = compute_features(orders, FEATURE_DATE).collect()[0]

    assert row.orders_7d == 1 and row.spend_7d == 10.0
    assert row.orders_30d == 2 and row.spend_30d == 30.0
    assert row.orders_lifetime == 3
    assert str(row.last_order_date) == "2026-06-30"
    assert str(row.feature_date) == FEATURE_DATE


def test_point_in_time_excludes_future_orders(spark):
    from lakehouse.features.customer_order_features import compute_features

    orders = spark.createDataFrame(
        [
            _order("c-1", "2026-06-30", 10.0),
            _order("c-1", "2026-07-02", 999.0),  # after feature_date → leakage if counted
        ]
    )
    row = compute_features(orders, FEATURE_DATE).collect()[0]
    assert row.orders_lifetime == 1
    assert row.spend_7d == 10.0


def test_customers_without_recent_orders_get_zeros(spark):
    from lakehouse.features.customer_order_features import compute_features

    orders = spark.createDataFrame([_order("c-2", "2025-01-01", 5.0)])
    row = compute_features(orders, FEATURE_DATE).collect()[0]
    assert row.orders_7d == 0 and row.spend_7d == 0.0
    assert row.orders_30d == 0 and row.spend_30d == 0.0
    assert row.orders_lifetime == 1


def test_favorite_currency_majority_and_deterministic_ties(spark):
    from lakehouse.features.customer_order_features import compute_features

    orders = spark.createDataFrame(
        [
            _order("c-1", "2026-06-29", 10.0, "USD"),
            _order("c-1", "2026-06-28", 10.0, "USD"),
            _order("c-1", "2026-06-27", 10.0, "EUR"),
            _order("c-2", "2026-06-29", 10.0, "USD"),
            _order("c-2", "2026-06-28", 10.0, "EUR"),  # tie → alphabetical → EUR
        ]
    )
    rows = {r.customer_id: r for r in compute_features(orders, FEATURE_DATE).collect()}
    assert rows["c-1"].favorite_currency == "USD"
    assert rows["c-2"].favorite_currency == "EUR"


def test_snapshot_drift_ratios(spark):
    from lakehouse.features.customer_order_features import snapshot_drift

    columns = ["customer_id", "spend_30d", "orders_30d"]
    previous = spark.createDataFrame([("c-1", 100.0, 10), ("c-2", 300.0, 30)], columns)
    current = spark.createDataFrame([("c-1", 400.0, 20), ("c-2", 400.0, 20)], columns)

    ratios = snapshot_drift(current, previous)
    assert ratios["spend_30d"] == pytest.approx(2.0)  # mean 200 → 400
    assert ratios["orders_30d"] == pytest.approx(1.0)  # mean 20 → 20


def test_snapshot_drift_handles_zero_baseline(spark):
    from lakehouse.features.customer_order_features import snapshot_drift

    columns = ["customer_id", "spend_30d", "orders_30d"]
    previous = spark.createDataFrame([("c-1", 0.0, 0)], columns)
    current = spark.createDataFrame([("c-1", 50.0, 0)], columns)

    ratios = snapshot_drift(current, previous)
    assert ratios["spend_30d"] == float("inf")
    assert ratios["orders_30d"] == 1.0
