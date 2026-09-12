"""Unit tests for EmbeddingService + the hashing embedder."""

from __future__ import annotations

import pytest
from app.core.config import get_settings
from app.services.embedding_service import (
    EmbeddingService,
    HashingEmbedder,
    _l2_normalize,
)


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b, strict=False))
    na = sum(x * x for x in a) ** 0.5
    nb = sum(x * x for x in b) ** 0.5
    return dot / (na * nb) if na and nb else 0.0


def test_l2_normalize_unit_length() -> None:
    v = _l2_normalize([3.0, 4.0])
    assert pytest.approx(sum(x * x for x in v) ** 0.5, rel=1e-6) == 1.0


def test_l2_normalize_zero_vector_is_safe() -> None:
    assert _l2_normalize([0.0, 0.0]) == [0.0, 0.0]


def test_hashing_embedder_is_deterministic() -> None:
    emb = HashingEmbedder(dim=64)
    a = emb.encode(["the quick brown fox"])[0]
    b = emb.encode(["the quick brown fox"])[0]
    assert a == b
    assert len(a) == 64


@pytest.mark.asyncio
async def test_embed_batch_returns_one_normalized_vector_per_input() -> None:
    service = EmbeddingService(get_settings(), embedder=HashingEmbedder(dim=128))
    vectors = await service.embed_batch(["hello world", "foo bar", "baz"])
    assert len(vectors) == 3
    for v in vectors:
        assert len(v) == 128
        assert pytest.approx(sum(x * x for x in v) ** 0.5, rel=1e-6) == 1.0


@pytest.mark.asyncio
async def test_related_texts_are_closer_than_unrelated() -> None:
    service = EmbeddingService(get_settings(), embedder=HashingEmbedder(dim=256))
    q, related, unrelated = await service.embed_batch(
        [
            "machine learning models for search",
            "search using machine learning models",
            "the weather in the mountains today",
        ]
    )
    assert _cosine(q, related) > _cosine(q, unrelated)


@pytest.mark.asyncio
async def test_embed_empty_input() -> None:
    service = EmbeddingService(get_settings(), embedder=HashingEmbedder(dim=32))
    assert await service.embed_batch([]) == []
