"""Unit tests for the local hashing embedder."""

from __future__ import annotations

import math

import pytest

from app.rag.embeddings import LocalHashEmbedder


@pytest.mark.asyncio
async def test_deterministic_and_normalized() -> None:
    emb = LocalHashEmbedder(dimension=256)
    v1 = await emb.aembed_query("the contract termination clause")
    v2 = await emb.aembed_query("the contract termination clause")
    assert v1 == v2  # deterministic
    assert math.isclose(math.sqrt(sum(x * x for x in v1)), 1.0, rel_tol=1e-6)
    assert len(v1) == 256


@pytest.mark.asyncio
async def test_similar_text_scores_higher_than_unrelated() -> None:
    emb = LocalHashEmbedder(dimension=512)

    def cos(a, b):
        return sum(x * y for x, y in zip(a, b, strict=False))

    q = await emb.aembed_query("termination clause notice period")
    related = await emb.aembed_query("the termination clause requires a 30 day notice period")
    unrelated = await emb.aembed_query("the patient was prescribed antibiotics for infection")

    assert cos(q, related) > cos(q, unrelated)


@pytest.mark.asyncio
async def test_embed_documents_batches() -> None:
    emb = LocalHashEmbedder(dimension=128)
    vecs = await emb.aembed_documents(["alpha beta", "gamma delta"])
    assert len(vecs) == 2
    assert all(len(v) == 128 for v in vecs)
