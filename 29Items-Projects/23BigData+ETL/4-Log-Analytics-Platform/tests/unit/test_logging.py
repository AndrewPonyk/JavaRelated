"""Structured logging: JSON shape, extra attributes, idempotent configuration."""

from __future__ import annotations

import json
import logging

from log_analytics.common.logging import JsonFormatter, configure_logging


def _format(record: logging.LogRecord) -> dict:
    return json.loads(JsonFormatter(service="test-svc").format(record))


def _record(msg: str = "hello", **extra) -> logging.LogRecord:
    record = logging.LogRecord(
        name="test.logger",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg=msg,
        args=(),
        exc_info=None,
    )
    for key, value in extra.items():
        setattr(record, key, value)
    return record


def test_json_payload_matches_logevent_shape() -> None:
    payload = _format(_record())
    assert payload["service"] == "test-svc"
    assert payload["level"] == "INFO"
    assert payload["message"] == "hello"
    assert "timestamp" in payload


def test_extra_fields_become_attributes() -> None:
    payload = _format(_record(rejected=7, rule_id="r1"))
    assert payload["attributes"] == {"rejected": 7, "rule_id": "r1"}


def test_exceptions_are_captured() -> None:
    try:
        raise ValueError("boom")
    except ValueError:
        import sys

        record = logging.LogRecord(
            name="x",
            level=logging.ERROR,
            pathname=__file__,
            lineno=1,
            msg="failed",
            args=(),
            exc_info=sys.exc_info(),
        )
    payload = json.loads(JsonFormatter(service="s").format(record))
    assert "ValueError: boom" in payload["exception"]


def test_configure_logging_is_idempotent() -> None:
    configure_logging(service="svc-a")
    configure_logging(service="svc-a")
    assert len(logging.getLogger().handlers) == 1
