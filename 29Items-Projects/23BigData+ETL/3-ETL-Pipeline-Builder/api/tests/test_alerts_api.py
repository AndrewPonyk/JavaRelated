import asyncio
import time

from app.config import get_settings
from app.services.alert_service import AlertService


def make_alert(alert_id: str, ts_offset_s: float = 0.0, severity: str = "warning") -> dict:
    return {
        "alert_id": alert_id,
        "metric": "orders_per_second",
        "value": 500.0,
        "score": 6.2,
        "baseline_mean": 100.0,
        "severity": severity,
        "message": "orders_per_second deviates from baseline",
        "triggered_at_ms": int((time.time() + ts_offset_s) * 1000),
    }


def seed(fake_redis, *alerts) -> AlertService:
    service = AlertService(client=fake_redis)
    for alert in alerts:
        asyncio.run(service.add(alert))
    return service


def test_alert_feed_returns_newest_first(client, fake_redis):
    seed(fake_redis, make_alert("a1", -20), make_alert("a2", -10), make_alert("a3", 0))

    body = client.get("/api/v1/alerts").json()

    assert [a["alert_id"] for a in body] == ["a3", "a2", "a1"]
    assert body[0]["acknowledged"] is False
    assert body[0]["severity"] == "warning"


def test_alert_feed_respects_limit(client, fake_redis):
    seed(fake_redis, *(make_alert(f"a{i}", -i) for i in range(10)))

    body = client.get("/api/v1/alerts?limit=3").json()

    assert len(body) == 3


def test_acknowledge_marks_alert(client, fake_redis):
    seed(fake_redis, make_alert("a1"))

    response = client.post("/api/v1/alerts/a1/ack")
    assert response.status_code == 200
    assert response.json() == {"alert_id": "a1", "acknowledged": True}

    body = client.get("/api/v1/alerts").json()
    assert body[0]["acknowledged"] is True


def test_acknowledge_unknown_alert_404(client):
    assert client.post("/api/v1/alerts/ghost/ack").status_code == 404


def test_store_trims_to_recent_cap(monkeypatch, fake_redis):
    monkeypatch.setenv("ALERTS_RECENT_CAP", "3")
    get_settings.cache_clear()
    service = seed(fake_redis, *(make_alert(f"a{i}", i) for i in range(5)))

    recent = asyncio.run(service.recent(limit=50))

    assert [a.alert_id for a in recent] == ["a4", "a3", "a2"]  # oldest two evicted
    assert asyncio.run(service.acknowledge("a0")) is False  # evicted → unknown


def test_malformed_envelope_is_discarded(fake_redis):
    service = seed(fake_redis, {"metric": "x"})  # no alert_id / timestamp

    assert asyncio.run(service.recent()) == []


def test_consumer_ingest_parses_and_stores(fake_redis):
    from app.config import get_settings
    from app.services.alerts_consumer import AlertsConsumer

    service = AlertService(client=fake_redis)
    consumer = AlertsConsumer(get_settings(), service)
    import json

    asyncio.run(consumer._ingest(json.dumps(make_alert("k1")).encode()))
    asyncio.run(consumer._ingest(b"{broken json"))  # dropped, not fatal

    assert consumer.alerts_ingested == 1
    assert [a.alert_id for a in asyncio.run(service.recent())] == ["k1"]
