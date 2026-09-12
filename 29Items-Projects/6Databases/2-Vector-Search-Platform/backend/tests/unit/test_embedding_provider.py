"""Unit tests for embedder selection + error wrapping."""

from __future__ import annotations

import pytest
from app.core.config import Settings
from app.core.exceptions import EmbeddingError
from app.services.embedding_service import (
    EmbeddingService,
    HashingEmbedder,
    _build_embedder,
)


def _settings(**overrides: object) -> Settings:
    base: dict[str, object] = {"app_env": "development", "api_key": "strong-key"}
    base.update(overrides)
    return Settings(**base)  # type: ignore[arg-type]


def test_build_embedder_hashing() -> None:
    embedder = _build_embedder(_settings(embedding_provider="hashing"))
    assert isinstance(embedder, HashingEmbedder)


def test_build_embedder_auto_falls_back_to_hashing() -> None:
    # sentence-transformers is not installed in the test env -> auto must fall back.
    embedder = _build_embedder(_settings(embedding_provider="auto"))
    assert isinstance(embedder, HashingEmbedder)


class _BoomEmbedder:
    name = "boom"
    dim = 4

    def encode(self, texts: list[str]) -> list[list[float]]:
        raise RuntimeError("encode failed")


@pytest.mark.asyncio
async def test_embed_batch_wraps_encode_errors() -> None:
    service = EmbeddingService(_settings(), embedder=_BoomEmbedder())
    with pytest.raises(EmbeddingError):
        await service.embed_batch(["x"])


@pytest.mark.asyncio
async def test_provider_name_and_dim_exposed() -> None:
    service = EmbeddingService(_settings(), embedder=HashingEmbedder(dim=16))
    assert service.provider_name == "hashing"
    assert service.dim == 16
