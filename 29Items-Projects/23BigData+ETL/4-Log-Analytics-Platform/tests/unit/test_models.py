"""Contract tests for the shared data models — every Kafka topic speaks these shapes."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from log_analytics.common.models import AlertEvent, AnomalyRecord, LogEvent, LogLevel


class TestLogEvent:
    def test_coerces_epoch_timestamp_and_level_alias(self) -> None:
        event = LogEvent.model_validate(
            {"timestamp": 1_780_000_000, "service": "checkout", "level": "warning", "message": "x"}
        )
        assert event.timestamp.tzinfo is not None
        assert event.level is LogLevel.WARN

    def test_rejects_unparseable_timestamp(self) -> None:
        with pytest.raises(ValidationError, match="timestamp"):
            LogEvent.model_validate({"timestamp": "not-a-time", "service": "s", "message": "x"})

    def test_extra_fields_ignored(self) -> None:
        event = LogEvent.model_validate(
            {"timestamp": "2026-07-01T12:00:00Z", "service": "s", "message": "x", "zzz": 1}
        )
        assert not hasattr(event, "zzz")

    def test_doc_id_is_deterministic_and_content_sensitive(self) -> None:
        base = {
            "timestamp": "2026-07-01T12:00:00Z",
            "service": "s",
            "message": "m",
            "host": "h1",
        }
        a = LogEvent.model_validate(base)
        b = LogEvent.model_validate(base)
        c = LogEvent.model_validate({**base, "message": "different"})
        assert a.doc_id() == b.doc_id()
        assert a.doc_id() != c.doc_id()


class TestAnomalyRecord:
    def test_score_bounds_enforced(self) -> None:
        payload = {
            "service": "s",
            "window_start": "2026-07-01T12:00:00Z",
            "window_end": "2026-07-01T12:01:00Z",
            "score": 1.5,
            "is_anomaly": True,
        }
        with pytest.raises(ValidationError):
            AnomalyRecord.model_validate(payload)
        record = AnomalyRecord.model_validate({**payload, "score": 0.97})
        assert record.score == 0.97


class TestAlertEvent:
    def test_default_dedup_key_from_rule_and_service(self) -> None:
        alert = AlertEvent(rule_id="r1", title="t", service="checkout")
        assert alert.dedup_key == "r1|checkout"

    def test_explicit_dedup_key_wins(self) -> None:
        alert = AlertEvent(rule_id="r1", title="t", service="s", dedup_key="custom")
        assert alert.dedup_key == "custom"

    def test_created_at_is_utc(self) -> None:
        alert = AlertEvent(rule_id="r", title="t")
        assert alert.created_at.tzinfo is not None
        assert alert.created_at <= datetime.now(timezone.utc)
