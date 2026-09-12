"""Logging setup: JSON formatter validity and mode switching."""

import json
import logging
import sys

from app.core.logging import JsonFormatter, configure_logging


def _record_with_exception() -> logging.LogRecord:
    try:
        raise ValueError("boom")
    except ValueError:
        return logging.LogRecord(
            "tsa.test", logging.ERROR, __file__, 1, "ingest %s", ("failed",), sys.exc_info()
        )


def test_json_formatter_emits_parseable_lines_with_exception():
    payload = json.loads(JsonFormatter().format(_record_with_exception()))
    assert payload["level"] == "ERROR"
    assert payload["logger"] == "tsa.test"
    assert payload["message"] == "ingest failed"
    assert "ValueError: boom" in payload["exc"]
    assert payload["ts"].endswith("+00:00")


def test_configure_logging_switches_formatter_and_level():
    root = logging.getLogger()
    saved_handlers, saved_level = root.handlers[:], root.level
    try:
        configure_logging("DEBUG", "json")
        assert root.level == logging.DEBUG
        assert isinstance(root.handlers[0].formatter, JsonFormatter)

        configure_logging("WARNING", "plain")
        assert root.level == logging.WARNING
        assert not isinstance(root.handlers[0].formatter, JsonFormatter)
        assert logging.getLogger("cassandra").level == logging.WARNING
    finally:
        root.handlers = saved_handlers
        root.setLevel(saved_level)
