"""Embedding providers.

``LocalHashEmbedder`` is a real, deterministic hashing vectorizer (bag-of-words +
bigrams hashed into a fixed-dimension, L2-normalized vector). Cosine similarity over
these vectors retrieves by lexical overlap — good enough for local dev and tests, and
fully offline. ``BedrockEmbedder`` wraps Titan embeddings via LangChain for production.
"""

from __future__ import annotations

import hashlib
import math
import re
from functools import lru_cache

from app.core.config import settings
from app.rag.errors import EmbeddingError  # noqa: F401  (public re-export)
from app.rag.types import Embedder

_TOKEN_RE = re.compile(r"[a-z0-9]+")


def _tokenize(text: str) -> list[str]:
    return _TOKEN_RE.findall(text.lower())


def _features(text: str) -> list[str]:
    tokens = _tokenize(text)
    bigrams = [f"{a}_{b}" for a, b in zip(tokens, tokens[1:], strict=False)]
    return tokens + bigrams


class LocalHashEmbedder:
    """Deterministic, dependency-free hashing embedder."""

    def __init__(self, dimension: int) -> None:
        self.dimension = dimension

    def _embed(self, text: str) -> list[float]:
        vec = [0.0] * self.dimension
        for feat in _features(text):
            h = hashlib.blake2b(feat.encode("utf-8"), digest_size=8).digest()
            idx = int.from_bytes(h[:4], "big") % self.dimension
            sign = 1.0 if h[4] & 1 else -1.0  # signed hashing reduces collisions
            vec[idx] += sign
        norm = math.sqrt(sum(v * v for v in vec))
        if norm == 0.0:
            return vec
        return [v / norm for v in vec]

    async def aembed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(t) for t in texts]

    async def aembed_query(self, text: str) -> list[float]:
        return self._embed(text)


class BedrockEmbedder:
    """Amazon Titan embeddings via ``langchain_aws.BedrockEmbeddings``."""

    def __init__(self, model_id: str, region: str, dimension: int) -> None:
        from langchain_aws import BedrockEmbeddings  # lazy: prod-only dependency

        self.dimension = dimension
        self._client = BedrockEmbeddings(model_id=model_id, region_name=region)

    async def aembed_documents(self, texts: list[str]) -> list[list[float]]:
        try:
            return await self._client.aembed_documents(texts)
        except Exception as exc:  # noqa: BLE001 — normalize to typed error
            raise EmbeddingError(str(exc)) from exc

    async def aembed_query(self, text: str) -> list[float]:
        try:
            return await self._client.aembed_query(text)
        except Exception as exc:  # noqa: BLE001
            raise EmbeddingError(str(exc)) from exc


@lru_cache
def get_embedder() -> Embedder:
    if settings.rag_backend == "bedrock":
        return BedrockEmbedder(
            settings.bedrock_embed_model_id, settings.aws_region, settings.embedding_dimension
        )
    return LocalHashEmbedder(settings.local_embedding_dimension)
