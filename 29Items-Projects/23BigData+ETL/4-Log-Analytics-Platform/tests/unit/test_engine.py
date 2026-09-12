"""Alerting engine: rules-driven anomaly conversion, dedup, dispatch, silent detection."""

from __future__ import annotations

import asyncio
import json

import httpx
import pytest

from log_analytics.alerting.engine import AlertingEngine, Deduplicator, find_silent_services
from log_analytics.common.config import Settings
from log_analytics.common.kafka import TOPIC_ALERTS, TOPIC_LOGS_ANOMALIES
from log_analytics.common.models import AlertEvent, AlertSeverity


class CaptureNotifier:
    def __init__(self, name: str = "capture", ok: bool = True) -> None:
        self.name = name
        self.ok = ok
        self.alerts: list[AlertEvent] = []

    async def send(self, alert: AlertEvent) -> bool:
        self.alerts.append(alert)
        return self.ok


class RecordingTransport(httpx.MockTransport):
    """MockTransport that records requests and serves canned OpenSearch responses."""

    def __init__(self) -> None:
        self.requests: list[httpx.Request] = []
        self.agg_services: dict[str, list[str]] = {}  # range gte → service buckets
        super().__init__(self._handle)

    def _handle(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        if request.url.path.endswith("/_search"):
            body = json.loads(request.content)
            gte = body["query"]["range"]["@timestamp"]["gte"]
            buckets = [{"key": s, "doc_count": 1} for s in self.agg_services.get(gte, [])]
            return httpx.Response(200, json={"aggregations": {"services": {"buckets": buckets}}})
        return httpx.Response(201, json={"result": "created"})


def make_engine(**settings_overrides) -> tuple[AlertingEngine, dict, RecordingTransport]:
    settings_overrides.setdefault("rules_path", "config/alert_rules.yaml")
    settings = Settings(
        _env_file=None,
        redis_url="",  # in-memory dedup
        **settings_overrides,
    )
    notifiers = {"log": CaptureNotifier("log"), "slack": CaptureNotifier("slack")}
    transport = RecordingTransport()
    engine = AlertingEngine(settings, notifiers=notifiers, transport=transport)  # type: ignore[arg-type]
    return engine, notifiers, transport


def anomaly_payload(score: float, service: str = "checkout") -> dict:
    return {
        "service": service,
        "window_start": "2026-07-01T12:00:00Z",
        "window_end": "2026-07-01T12:01:00Z",
        "score": score,
        "is_anomaly": score >= 0.8,
        "model_version": "20260701-000000",
        "log_count": 3000.0,
        "error_ratio": 0.4,
    }


class TestAnomalyConversion:
    def test_below_all_thresholds_is_ignored(self) -> None:
        engine, _, _ = make_engine()
        assert engine.anomaly_to_alert(engine.parse_anomaly(anomaly_payload(0.5))) is None

    def test_warning_rule_matches(self) -> None:
        engine, _, _ = make_engine()
        alert, channels = engine.anomaly_to_alert(engine.parse_anomaly(anomaly_payload(0.85)))
        assert alert.rule_id == "ml-anomaly-warning"
        assert alert.severity is AlertSeverity.WARNING
        assert channels == ("log", "slack")

    def test_highest_threshold_rule_wins(self) -> None:
        engine, _, _ = make_engine()
        alert, channels = engine.anomaly_to_alert(engine.parse_anomaly(anomaly_payload(0.97)))
        assert alert.rule_id == "ml-anomaly-critical"
        assert alert.severity is AlertSeverity.CRITICAL
        assert "pagerduty" in channels

    def test_features_folded_from_flat_payload(self) -> None:
        engine, _, _ = make_engine()
        record = engine.parse_anomaly(anomaly_payload(0.9))
        assert record.features["log_count"] == 3000.0
        assert record.features["error_ratio"] == 0.4

    def test_baseline_thresholds_without_rules(self, tmp_path) -> None:
        empty_rules = tmp_path / "rules.yaml"
        empty_rules.write_text("rules: []", encoding="utf-8")
        engine, _, _ = make_engine(rules_path=str(empty_rules))
        assert engine.anomaly_to_alert(engine.parse_anomaly(anomaly_payload(0.7))) is None
        warn, _ = engine.anomaly_to_alert(engine.parse_anomaly(anomaly_payload(0.85)))
        crit, _ = engine.anomaly_to_alert(engine.parse_anomaly(anomaly_payload(0.99)))
        assert warn.severity is AlertSeverity.WARNING
        assert crit.severity is AlertSeverity.CRITICAL


class TestHandle:
    def test_anomaly_dispatched_and_indexed(self) -> None:
        engine, notifiers, transport = make_engine()
        asyncio.run(engine.handle(TOPIC_LOGS_ANOMALIES, anomaly_payload(0.9)))
        assert len(notifiers["slack"].alerts) == 1
        index_requests = [r for r in transport.requests if "/la-alerts/_doc/" in r.url.path]
        assert len(index_requests) == 1
        doc = json.loads(index_requests[0].content)
        assert doc["rule_id"] == "ml-anomaly-warning"
        assert "@timestamp" in doc and "created_at" not in doc

    def test_dedup_suppresses_repeat_alerts(self) -> None:
        engine, notifiers, _ = make_engine()
        asyncio.run(engine.handle(TOPIC_LOGS_ANOMALIES, anomaly_payload(0.9)))
        asyncio.run(engine.handle(TOPIC_LOGS_ANOMALIES, anomaly_payload(0.9)))
        assert len(notifiers["slack"].alerts) == 1

    def test_pattern_alert_channels_resolved_from_rules(self) -> None:
        engine, notifiers, _ = make_engine()
        payload = {
            "rule_id": "high-error-ratio",
            "severity": "warning",
            "source": "pattern",
            "service": "payments",
            "title": "[High error ratio] payments",
            "description": "",
            "dedup_key": "high-error-ratio|payments|2026-07-01 12:00:00",
            "context": json.dumps({"observed": 0.1, "threshold": 0.05}),
        }
        asyncio.run(engine.handle(TOPIC_ALERTS, payload))
        assert len(notifiers["slack"].alerts) == 1
        assert notifiers["slack"].alerts[0].context["observed"] == 0.1

    def test_failed_channel_falls_back_to_log(self) -> None:
        engine, notifiers, _ = make_engine()
        notifiers["slack"].ok = False
        asyncio.run(engine.handle(TOPIC_LOGS_ANOMALIES, anomaly_payload(0.9)))
        assert len(notifiers["log"].alerts) == 1  # fallback delivery


class TestSilentServices:
    def test_pure_set_logic(self) -> None:
        assert find_silent_services({"a", "b", "c"}, {"a"}) == {"b", "c"}
        assert find_silent_services(set(), set()) == set()

    def test_check_builds_alerts_from_aggregations(self) -> None:
        engine, _, transport = make_engine(silent_service_after_minutes=10)
        transport.agg_services["now-1440m"] = ["checkout", "payments", "auth"]
        transport.agg_services["now-10m"] = ["checkout"]
        alerts = asyncio.run(engine.check_silent_services())
        assert [a.service for a in alerts] == ["auth", "payments"]
        assert all(a.rule_id == "service-silent" for a in alerts)


class TestDeduplicator:
    def test_in_memory_fires_once_per_key(self) -> None:
        dedup = Deduplicator(None, ttl_seconds=300)
        assert dedup.should_fire("k1") is True
        assert dedup.should_fire("k1") is False
        assert dedup.should_fire("k2") is True

    def test_expired_keys_fire_again(self, monkeypatch) -> None:
        now = [1000.0]
        monkeypatch.setattr("log_analytics.alerting.engine.time.monotonic", lambda: now[0])
        dedup = Deduplicator(None, ttl_seconds=60)
        assert dedup.should_fire("k") is True
        now[0] += 61
        assert dedup.should_fire("k") is True


@pytest.mark.parametrize("bad_context", ["{not json", ""])
def test_pattern_alert_with_unparseable_context_survives(bad_context: str) -> None:
    engine, notifiers, _ = make_engine()
    payload = {
        "rule_id": "error-burst",
        "severity": "critical",
        "title": "x",
        "service": "s",
        "dedup_key": f"eb|{bad_context}",
        "context": bad_context,
    }
    asyncio.run(engine.handle(TOPIC_ALERTS, payload))
    assert len(notifiers["log"].alerts) == 1
