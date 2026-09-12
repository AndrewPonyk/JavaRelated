"""Unit tests for the audit publisher and Trino preview service (no servers)."""

from __future__ import annotations

import pytest

from app.core.config import get_settings
from app.db.models import Dataset, DatasetLayer
from app.services.audit_publisher import (
    KafkaAuditPublisher,
    NullAuditPublisher,
    get_audit_publisher,
)
from app.services.trino_client import (
    TrinoPreviewService,
    TrinoUnavailableError,
    _quote_identifier,
    get_preview_service,
    resolve_trino_table,
)

# --- Audit publisher ------------------------------------------------------------


class FakeProducer:
    def __init__(self, fail: bool = False):
        self.fail = fail
        self.sent: list[tuple[str, dict]] = []

    def send(self, topic, value):
        if self.fail:
            raise ConnectionError("broker down")
        self.sent.append((topic, value))


def test_null_publisher_is_default(client):
    assert isinstance(get_audit_publisher(), NullAuditPublisher)
    assert get_audit_publisher().publish({"action": "x"}) is None


def test_kafka_publisher_selected_when_enabled(monkeypatch):
    monkeypatch.setenv("KAFKA_AUDIT_ENABLED", "true")
    get_settings.cache_clear()
    get_audit_publisher.cache_clear()
    assert isinstance(get_audit_publisher(), KafkaAuditPublisher)


def test_kafka_publisher_sends_json_event():
    fake = FakeProducer()
    publisher = KafkaAuditPublisher("broker:9092", "platform.audit.v1", lambda **kw: fake)

    publisher.publish({"action": "dataset.created", "entity_id": "abc"})

    assert fake.sent == [("platform.audit.v1", {"action": "dataset.created", "entity_id": "abc"})]


def test_kafka_publish_failure_never_raises():
    publisher = KafkaAuditPublisher(
        "broker:9092", "platform.audit.v1", lambda **kw: FakeProducer(fail=True)
    )
    publisher.publish({"action": "dataset.created"})  # must not raise


def test_kafka_producer_created_once():
    calls = []

    def factory(**kwargs):
        calls.append(kwargs)
        return FakeProducer()

    publisher = KafkaAuditPublisher("broker:9092", "t", factory)
    publisher.publish({"a": 1})
    publisher.publish({"a": 2})
    assert len(calls) == 1
    assert calls[0]["bootstrap_servers"] == "broker:9092"
    assert calls[0]["max_block_ms"] == 2000  # a down broker must not stall requests


def test_kafka_publisher_backs_off_while_broker_down():
    calls = []

    def factory(**kwargs):
        calls.append(1)
        return FakeProducer(fail=True)

    publisher = KafkaAuditPublisher("broker:9092", "t", factory, failure_backoff_seconds=60.0)
    publisher.publish({"a": 1})  # fails → opens the breaker
    publisher.publish({"a": 2})  # inside the window → skipped entirely
    publisher.publish({"a": 3})
    assert len(calls) == 1


def test_kafka_publisher_reconnects_after_backoff_window():
    calls = []

    def factory(**kwargs):
        calls.append(1)
        return FakeProducer(fail=True)

    publisher = KafkaAuditPublisher("broker:9092", "t", factory, failure_backoff_seconds=0.0)
    publisher.publish({"a": 1})
    publisher.publish({"a": 2})  # window already elapsed → fresh producer attempt
    assert len(calls) == 2


def test_audit_fanout_happens_with_committed_data(client, monkeypatch):
    """Events reach the publisher only for successfully committed mutations."""
    events: list[dict] = []

    class Recorder:
        def publish(self, event):
            events.append(event)

    monkeypatch.setattr("app.services.dataset_service.get_audit_publisher", lambda: Recorder())

    created = client.post(
        "/api/v1/datasets",
        json={
            "name": "sales.fanout",
            "layer": "gold",
            "owner_email": "e@example.com",
            "s3_path": "s3://b/sales/fanout",
        },
    )
    assert created.status_code == 201
    assert [e["action"] for e in events] == ["dataset.created"]
    assert events[0]["dataset_name"] == "sales.fanout"

    # a rejected mutation publishes nothing
    events.clear()
    duplicate = client.post(
        "/api/v1/datasets",
        json={
            "name": "sales.fanout",
            "layer": "gold",
            "owner_email": "e@example.com",
            "s3_path": "s3://b/sales/fanout",
        },
    )
    assert duplicate.status_code == 409
    assert events == []


# --- Trino preview ----------------------------------------------------------------


class FakeCursor:
    description = [("order_id",), ("amount",)]

    def execute(self, sql):
        self.sql = sql

    def fetchall(self):
        return [("o-1", 10.5), ("o-2", 99.0)]


class FakeConnection:
    def __init__(self):
        self.closed = False
        self._cursor = FakeCursor()

    def cursor(self):
        return self._cursor

    def close(self):
        self.closed = True


def _dataset(name="sales.orders", layer=DatasetLayer.silver) -> Dataset:
    return Dataset(
        name=name, layer=layer, owner_email="e@example.com", s3_path="s3://b/sales/orders"
    )


def test_preview_returns_columns_and_rows(client):
    connection = FakeConnection()
    service = TrinoPreviewService(connection_factory=lambda: connection)

    result = service.preview(_dataset(), limit=10)

    assert result.columns == ["order_id", "amount"]
    assert result.rows == [["o-1", 10.5], ["o-2", 99.0]]
    assert result.source == "delta.silver.orders"
    assert connection.closed
    assert 'FROM "silver"."orders" LIMIT 10' in connection._cursor.sql


def test_preview_limit_is_clamped(client):
    connection = FakeConnection()
    TrinoPreviewService(connection_factory=lambda: connection).preview(_dataset(), limit=10_000)
    assert "LIMIT 100" in connection._cursor.sql  # PREVIEW_ROW_LIMIT default


def test_preview_wraps_connection_errors(client):
    def refuse():
        raise ConnectionRefusedError("no trino here")

    service = TrinoPreviewService(connection_factory=refuse)
    with pytest.raises(TrinoUnavailableError):
        service.preview(_dataset(), limit=5)


def test_resolve_trino_table_convention():
    assert resolve_trino_table(_dataset("sales.orders", DatasetLayer.silver)) == (
        "silver",
        "orders",
    )
    assert resolve_trino_table(_dataset("fct_daily_revenue", DatasetLayer.gold)) == (
        "gold",
        "fct_daily_revenue",
    )


def test_quote_identifier_rejects_injection():
    assert _quote_identifier("orders") == '"orders"'
    with pytest.raises(ValueError):
        _quote_identifier('orders"; drop table x --')
    with pytest.raises(ValueError):
        _quote_identifier("Orders")  # uppercase not allowed by convention


# --- Preview endpoint wiring --------------------------------------------------------


def test_preview_endpoint_with_fake_service(make_client, monkeypatch):
    client = make_client({get_preview_service: lambda: TrinoPreviewService(FakeConnection)})
    created = client.post(
        "/api/v1/datasets",
        json={
            "name": "sales.orders",
            "layer": "silver",
            "owner_email": "e@example.com",
            "s3_path": "s3://b/sales/orders",
        },
    ).json()

    response = client.get(f"/api/v1/datasets/{created['id']}/preview", params={"limit": 5})
    assert response.status_code == 200
    body = response.json()
    assert body["columns"] == ["order_id", "amount"]
    assert body["row_count"] == 2
    assert body["source"] == "delta.silver.orders"


def test_preview_endpoint_maps_unavailable_to_502(make_client):
    def refuse():
        raise ConnectionRefusedError("no trino")

    client = make_client({get_preview_service: lambda: TrinoPreviewService(refuse)})
    created = client.post(
        "/api/v1/datasets",
        json={
            "name": "sales.orders",
            "layer": "silver",
            "owner_email": "e@example.com",
            "s3_path": "s3://b/sales/orders",
        },
    ).json()

    response = client.get(f"/api/v1/datasets/{created['id']}/preview")
    assert response.status_code == 502
    assert response.json()["title"] == "Query engine unavailable"
