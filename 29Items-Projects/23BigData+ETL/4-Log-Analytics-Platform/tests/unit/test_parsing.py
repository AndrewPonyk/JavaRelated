"""Unit tests for common/parsing.py — the pipeline's front door normalization."""

from __future__ import annotations

from datetime import datetime, timezone

import pytest

from log_analytics.common.parsing import (
    coerce_log_record,
    normalize_level,
    parse_timestamp,
    try_parse_json_line,
)


class TestNormalizeLevel:
    @pytest.mark.parametrize(
        ("raw", "expected"),
        [
            ("info", "INFO"),
            ("WARNING", "WARN"),
            ("warn", "WARN"),
            ("err", "ERROR"),
            ("SEVERE", "ERROR"),
            ("critical", "FATAL"),
            ("verbose", "TRACE"),
            ("  Error  ", "ERROR"),
        ],
    )
    def test_aliases(self, raw: str, expected: str) -> None:
        assert normalize_level(raw) == expected

    def test_unknown_and_empty_default_to_info(self) -> None:
        assert normalize_level("banana") == "INFO"
        assert normalize_level(None) == "INFO"
        assert normalize_level("") == "INFO"


class TestParseTimestamp:
    def test_iso_with_z(self) -> None:
        parsed = parse_timestamp("2026-07-01T12:00:00Z")
        assert parsed == datetime(2026, 7, 1, 12, 0, tzinfo=timezone.utc)

    def test_iso_naive_assumed_utc(self) -> None:
        parsed = parse_timestamp("2026-07-01T12:00:00")
        assert parsed is not None and parsed.tzinfo is not None

    def test_log4j_comma_millis(self) -> None:
        parsed = parse_timestamp("2026-07-01 12:00:00,123")
        assert parsed is not None and parsed.microsecond == 123000

    def test_epoch_seconds_and_millis_agree(self) -> None:
        seconds = parse_timestamp(1_780_000_000)
        millis = parse_timestamp(1_780_000_000_000)
        assert seconds == millis
        assert seconds is not None and seconds.year >= 2026

    def test_datetime_passthrough_gets_tz(self) -> None:
        parsed = parse_timestamp(datetime(2026, 7, 1, 12, 0))
        assert parsed is not None and parsed.tzinfo is not None

    def test_garbage_returns_none(self) -> None:
        assert parse_timestamp("not a time") is None
        assert parse_timestamp(None) is None
        assert parse_timestamp("") is None


class TestJsonLine:
    def test_json_object_line(self) -> None:
        assert try_parse_json_line('{"level": "info"}') == {"level": "info"}

    def test_plain_text_line(self) -> None:
        assert try_parse_json_line("2026-07-01 INFO started") is None

    def test_json_array_is_not_a_record(self) -> None:
        assert try_parse_json_line("[1, 2]") is None

    def test_broken_json(self) -> None:
        assert try_parse_json_line('{"level": ') is None


class TestCoerceLogRecord:
    def test_alias_mapping(self) -> None:
        record = coerce_log_record(
            {
                "@timestamp": "2026-07-01T12:00:00Z",
                "severity": "warning",
                "msg": "hello",
                "app": "checkout",
                "hostname": "ip-10-0-1-11",
            }
        )
        assert record["timestamp"] == "2026-07-01T12:00:00Z"
        assert record["level"] == "warning"
        assert record["message"] == "hello"
        assert record["service"] == "checkout"
        assert record["host"] == "ip-10-0-1-11"

    def test_unknown_keys_become_attributes(self) -> None:
        record = coerce_log_record({"msg": "x", "service": "s", "custom_field": 42})
        assert record["attributes"] == {"custom_field": 42}

    def test_existing_attributes_merge_with_extras(self) -> None:
        record = coerce_log_record({"msg": "x", "service": "s", "attributes": {"a": 1}, "b": 2})
        assert record["attributes"] == {"a": 1, "b": 2}
