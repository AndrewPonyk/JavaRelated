"""Batch loader idempotency logic (pure, no Spark)."""

from __future__ import annotations

from lakehouse.ingestion.batch_file_loader import filter_new_files


def test_only_unseen_files_selected():
    candidates = ["s3a://landing/b.json", "s3a://landing/a.json", "s3a://landing/c.json"]
    seen = {"s3a://landing/b.json"}
    assert filter_new_files(candidates, seen) == ["s3a://landing/a.json", "s3a://landing/c.json"]


def test_everything_new_on_first_run():
    assert filter_new_files(["f2", "f1"], set()) == ["f1", "f2"]


def test_nothing_new_when_all_seen():
    assert filter_new_files(["f1"], {"f1"}) == []


def test_duplicate_candidates_collapse():
    assert filter_new_files(["f1", "f1"], set()) == ["f1"]
