"""Unit tests for sentence-aware chunking."""

from __future__ import annotations

from app.services.chunking import chunk_text


def test_short_text_is_single_chunk() -> None:
    assert chunk_text("Just one short sentence.", size=800) == ["Just one short sentence."]


def test_empty_text_yields_no_chunks() -> None:
    assert chunk_text("   ", size=800) == []


def test_long_text_splits_into_multiple_chunks() -> None:
    text = " ".join(f"This is sentence number {i}." for i in range(200))
    chunks = chunk_text(text, size=200, overlap=40)
    assert len(chunks) > 1
    assert all(len(c) <= 200 + 40 for c in chunks)  # allow overlap slack


def test_oversized_single_sentence_is_hard_split() -> None:
    text = "x" * 1000  # no sentence boundaries
    chunks = chunk_text(text, size=300, overlap=0)
    assert len(chunks) == 4
    assert "".join(chunks) == text
