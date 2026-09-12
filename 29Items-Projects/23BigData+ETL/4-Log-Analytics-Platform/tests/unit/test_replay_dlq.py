"""DLQ replay tooling: record parsing, filtering, dump/load round-trip, dry-run plan."""

from __future__ import annotations

import json
from pathlib import Path

from replay_dlq import (
    DlqRecord,
    build_parser,
    dump_records,
    load_records,
    parse_dlq_record,
    parse_payload,
    prepare_replay,
    summarize,
)


def _record(
    payload: str,
    error: str = "schema_validation_failed",
    partition: int = 0,
    offset: int = 1,
) -> DlqRecord:
    return DlqRecord(
        payload=payload,
        error=error,
        source_topic="logs.raw",
        source_partition=partition,
        source_offset=offset,
    )


def test_parse_dlq_record_tolerates_missing_fields() -> None:
    record = parse_dlq_record({})
    assert record.error == "unknown"
    assert record.payload == ""
    assert record.source_partition == 0


def test_coordinates_and_filename_are_stable() -> None:
    record = _record("{}", partition=3, offset=42)
    assert record.coordinates == "logs.raw:3:42"
    assert record.filename == "0003-000000000042.json"


def test_summarize_counts_by_error_most_frequent_first() -> None:
    records = [
        _record("x", error="schema_validation_failed"),
        _record("y", error="schema_validation_failed"),
        _record("z", error="unreadable_dlq_record"),
    ]
    assert list(summarize(records).items()) == [
        ("schema_validation_failed", 2),
        ("unreadable_dlq_record", 1),
    ]


def test_parse_payload_accepts_only_json_objects() -> None:
    assert parse_payload(_record('{"service": "checkout"}')) == {"service": "checkout"}
    assert parse_payload(_record("{broken")) is None
    assert parse_payload(_record("[1, 2]")) is None
    assert parse_payload(_record("")) is None


def test_prepare_replay_splits_replayable_and_skipped() -> None:
    records = [
        _record('{"service": "checkout", "message": "ok"}', offset=1),
        _record("{not json", offset=2),
        _record('{"message": "no service key"}', offset=3),
    ]
    items, skipped = prepare_replay(records)
    assert [item.origin for item in items] == ["logs.raw:0:1", "logs.raw:0:3"]
    assert items[0].key == "checkout"
    assert items[1].key is None  # missing service → unkeyed
    assert [record.source_offset for record in skipped] == [2]


def test_prepare_replay_applies_error_filter_and_limit() -> None:
    records = [
        _record('{"a": 1}', error="schema_validation_failed", offset=1),
        _record('{"a": 2}', error="other_reason", offset=2),
        _record('{"a": 3}', error="schema_validation_failed", offset=3),
        _record('{"a": 4}', error="schema_validation_failed", offset=4),
    ]
    items, skipped = prepare_replay(records, error="schema_validation_failed", limit=2)
    assert [item.value["a"] for item in items] == [1, 3]
    assert skipped == []


def test_dump_then_load_round_trips(tmp_path: Path) -> None:
    records = [
        _record('{"service": "a"}', partition=1, offset=10),
        _record("{broken", partition=2, offset=20),
    ]
    assert dump_records(records, tmp_path) == 2
    assert load_records(tmp_path) == records


def test_dump_files_are_editable_json(tmp_path: Path) -> None:
    dump_records([_record("{broken", partition=0, offset=7)], tmp_path)
    path = tmp_path / "0000-000000000007.json"
    doc = json.loads(path.read_text(encoding="utf-8"))
    doc["payload"] = '{"timestamp": "2026-07-08T10:00:00Z", "service": "fixed"}'  # the fix
    path.write_text(json.dumps(doc), encoding="utf-8")

    items, skipped = prepare_replay(load_records(tmp_path))
    assert skipped == []
    assert items[0].key == "fixed"


def test_parser_defaults_are_safe() -> None:
    args = build_parser().parse_args(["replay"])
    assert args.execute is False  # dry-run unless explicitly told otherwise
    assert args.topic == "logs.raw"

    args = build_parser().parse_args(["inspect"])
    assert args.limit == 0
