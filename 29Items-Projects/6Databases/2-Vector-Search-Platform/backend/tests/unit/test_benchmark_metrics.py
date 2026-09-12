"""Unit tests for the pure benchmark metric functions (recall@k, MRR, percentile).

These are the numbers a benchmark report stands on, so they get golden-value tests.
"""

from __future__ import annotations

from app.services.benchmark_service import (
    mean_reciprocal_rank,
    percentile,
    recall_at_k,
)


def test_recall_at_k_full_hit() -> None:
    retrieved = ["a", "b", "c", "d"]
    relevant = {"a", "c"}
    assert recall_at_k(retrieved, relevant, k=4) == 1.0


def test_recall_at_k_partial_within_k() -> None:
    retrieved = ["x", "a", "y", "c"]
    relevant = {"a", "c"}
    # 'a' is within top-3, 'c' is not -> 1 of 2 relevant found.
    assert recall_at_k(retrieved, relevant, k=3) == 0.5


def test_recall_at_k_no_relevant_defined() -> None:
    assert recall_at_k(["a"], set(), k=5) == 0.0


def test_mrr_first_relevant_rank() -> None:
    # first relevant at rank 2 -> 0.5
    assert mean_reciprocal_rank(["x", "a", "b"], {"a"}) == 0.5


def test_mrr_none_found() -> None:
    assert mean_reciprocal_rank(["x", "y"], {"a"}) == 0.0


def test_percentile_basic() -> None:
    values = [10.0, 20.0, 30.0, 40.0, 50.0]
    assert percentile(values, 50) == 30.0
    assert percentile(values, 100) == 50.0
    assert percentile([], 95) == 0.0
