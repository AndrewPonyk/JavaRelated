"""Logging: JSON formatter, idempotent setup, Sentry degradation."""

from __future__ import annotations

import json
import logging
import sys

import pytest

from app.core import logging as log_mod
from app.core.config import Settings


def _record(level: int = logging.INFO, exc: bool = False) -> logging.LogRecord:
    exc_info = None
    if exc:
        try:
            raise ValueError("boom")
        except ValueError:
            exc_info = sys.exc_info()
    return logging.LogRecord(
        name="app.test",
        level=level,
        pathname=__file__,
        lineno=1,
        msg="hello %s",
        args=("world",),
        exc_info=exc_info,
    )


def test_json_formatter_emits_parseable_lines() -> None:
    payload = json.loads(log_mod._JsonFormatter().format(_record()))
    assert payload["level"] == "INFO"
    assert payload["logger"] == "app.test"
    assert payload["message"] == "hello world"
    assert "ts" in payload


def test_json_formatter_includes_exceptions() -> None:
    payload = json.loads(log_mod._JsonFormatter().format(_record(logging.ERROR, exc=True)))
    assert "boom" in payload["exc_info"]


def test_setup_logging_is_idempotent(monkeypatch: pytest.MonkeyPatch) -> None:
    """Streamlit reruns call setup on every interaction — handlers must not stack."""
    monkeypatch.setattr(log_mod, "_CONFIGURED", False)
    root = logging.getLogger()
    baseline = len(root.handlers)
    added: list[logging.Handler] = []
    try:
        log_mod.setup_logging()
        after_first = len(root.handlers)
        log_mod.setup_logging()
        after_second = len(root.handlers)
        added = root.handlers[baseline:]
        assert after_first == after_second == baseline + 1
    finally:
        for handler in added:
            root.removeHandler(handler)


def test_sentry_degrades_gracefully_when_sdk_missing(
    monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    try:
        import sentry_sdk  # noqa: F401

        pytest.skip("sentry-sdk installed in this environment")
    except ImportError:
        pass
    monkeypatch.setattr(
        "app.core.config.get_settings", lambda: Settings(sentry_dsn="https://key@sentry.example/1")
    )
    with caplog.at_level(logging.WARNING):
        log_mod._maybe_init_sentry()
    assert any("sentry-sdk is not installed" in r.message for r in caplog.records)


def test_sentry_skipped_without_dsn(
    monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    monkeypatch.setattr("app.core.config.get_settings", lambda: Settings())
    with caplog.at_level(logging.DEBUG):
        log_mod._maybe_init_sentry()
    assert not any("Sentry" in r.message or "sentry" in r.message for r in caplog.records)
