"""Unit tests for the recursive chunker."""

from __future__ import annotations

from app.core.config import settings
from app.rag.chunking import split_document


def test_empty_text_yields_no_chunks() -> None:
    assert split_document("") == []
    assert split_document("   \n  ") == []


def test_short_text_is_single_chunk() -> None:
    chunks = split_document("A short legal clause.", base_metadata={"document_id": "d1"})
    assert len(chunks) == 1
    assert chunks[0].metadata["document_id"] == "d1"
    assert chunks[0].metadata["chunk_index"] == 0


def test_long_text_splits_and_respects_chunk_size() -> None:
    text = ". ".join(f"Sentence number {i} about contracts" for i in range(400))
    chunks = split_document(text)
    assert len(chunks) > 1
    # Each chunk stays within the configured size (allowing the joiner slack).
    assert all(len(c.text) <= settings.rag_chunk_size + 1 for c in chunks)
    # chunk_index is contiguous from zero.
    assert [c.metadata["chunk_index"] for c in chunks] == list(range(len(chunks)))


def test_metadata_is_propagated() -> None:
    text = "x" * (settings.rag_chunk_size * 3)
    chunks = split_document(text, base_metadata={"document_id": "d9", "doc_type": "medical"})
    assert all(c.metadata["doc_type"] == "medical" for c in chunks)
    assert all(c.metadata["document_id"] == "d9" for c in chunks)
