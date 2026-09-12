"""Embedding generation with pluggable providers + an optional Redis cache.

Providers (selected by ``EMBEDDING_PROVIDER``):
  * ``sentence-transformers`` — the real model (``all-MiniLM-L6-v2`` by default).
  * ``hashing`` — a deterministic feature-hashing embedder (the "hashing trick"): no
    downloads, fully offline, and a genuine bag-of-tokens vector where texts that share
    terms are closer in cosine space. Used for dev/tests and as the ``auto`` fallback.

Every vector is L2-normalized so cosine similarity is consistent across all backends.
Embeddings are cached by ``sha256(provider:model:text)`` so repeated content skips encoding.
"""

from __future__ import annotations

import hashlib
import json
import re
from itertools import pairwise
from typing import Any, Protocol

import anyio
import structlog

from app.core.config import EmbeddingProvider, Settings, get_settings
from app.core.exceptions import EmbeddingError

log = structlog.get_logger(__name__)

_TOKEN_RE = re.compile(r"[a-z0-9]+")


def _l2_normalize(vector: list[float]) -> list[float]:
    norm = sum(x * x for x in vector) ** 0.5
    if norm == 0:
        return vector
    return [x / norm for x in vector]


def _tokenize(text: str) -> list[str]:
    return _TOKEN_RE.findall(text.lower())


class Embedder(Protocol):
    """Sync embedding backend. Called off the event loop via a threadpool."""

    name: str
    dim: int

    def encode(self, texts: list[str]) -> list[list[float]]: ...


class HashingEmbedder:
    """Deterministic feature-hashing embedder (offline, dependency-light).

    Uses signed hashing of unigram + bigram tokens into ``dim`` buckets. Stable across
    processes (hashlib, not the salted builtin ``hash``).
    """

    name = "hashing"

    def __init__(self, dim: int) -> None:
        self.dim = dim

    def _bucket_and_sign(self, token: str) -> tuple[int, float]:
        digest = hashlib.md5(token.encode("utf-8")).digest()
        bucket = int.from_bytes(digest[:4], "little") % self.dim
        sign = 1.0 if digest[4] & 1 else -1.0
        return bucket, sign

    def encode(self, texts: list[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for text in texts:
            vec = [0.0] * self.dim
            tokens = _tokenize(text)
            grams = tokens + [f"{a}_{b}" for a, b in pairwise(tokens)]
            for gram in grams:
                bucket, sign = self._bucket_and_sign(gram)
                vec[bucket] += sign
            vectors.append(vec)
        return vectors


class SentenceTransformerEmbedder:
    """Wraps a Sentence Transformers model (loaded lazily on first encode)."""

    name = "sentence-transformers"

    def __init__(self, model_name: str, dim: int, batch_size: int) -> None:
        self.dim = dim
        self._model_name = model_name
        self._batch_size = batch_size
        self._model: Any | None = None

    def _ensure_model(self) -> Any:
        if self._model is None:
            from sentence_transformers import SentenceTransformer  # heavy import

            log.info("embedding.model_load", model=self._model_name)
            self._model = SentenceTransformer(self._model_name)
            actual = int(self._model.get_sentence_embedding_dimension())
            if actual != self.dim:
                raise EmbeddingError(
                    f"EMBEDDING_DIM={self.dim} but model '{self._model_name}' emits {actual}."
                )
        return self._model

    def encode(self, texts: list[str]) -> list[list[float]]:
        model = self._ensure_model()
        arr = model.encode(texts, batch_size=self._batch_size, normalize_embeddings=False)
        return [list(map(float, row)) for row in arr]


def _build_embedder(settings: Settings) -> Embedder:
    provider = settings.embedding_provider
    dim = settings.embedding_dim

    if provider is EmbeddingProvider.hashing:
        return HashingEmbedder(dim)

    st = SentenceTransformerEmbedder(settings.embedding_model, dim, settings.embedding_batch_size)
    if provider is EmbeddingProvider.sentence_transformers:
        return st

    # auto: prefer the real model, fall back to hashing if it can't be imported/loaded.
    try:
        st._ensure_model()
        return st
    except Exception as exc:
        log.warning("embedding.fallback_to_hashing", reason=str(exc))
        return HashingEmbedder(dim)


class EmbeddingService:
    """Turn text into normalized embedding vectors, with an optional Redis cache."""

    def __init__(
        self,
        settings: Settings | None = None,
        *,
        redis: Any | None = None,
        embedder: Embedder | None = None,
    ) -> None:
        self._settings = settings or get_settings()
        self._redis = redis
        self._embedder = embedder or _build_embedder(self._settings)

    @property
    def provider_name(self) -> str:
        return self._embedder.name

    @property
    def dim(self) -> int:
        return self._embedder.dim

    def _cache_key(self, text: str) -> str:
        payload = f"{self._embedder.name}:{self._settings.embedding_model}:{text}"
        return f"emb:{hashlib.sha256(payload.encode()).hexdigest()}"

    async def embed(self, text: str) -> list[float]:
        return (await self.embed_batch([text]))[0]

    async def embed_batch(self, texts: list[str]) -> list[list[float]]:
        """Embed many strings, using the cache for hits and the embedder for misses."""
        if not texts:
            return []

        results: list[list[float] | None] = [None] * len(texts)
        miss_idx: list[int] = []
        for i, text in enumerate(texts):
            cached = await self._cache_get(self._cache_key(text))
            if cached is not None:
                results[i] = cached
            else:
                miss_idx.append(i)

        if miss_idx:
            try:
                raw = await anyio.to_thread.run_sync(
                    self._embedder.encode, [texts[i] for i in miss_idx]
                )
            except EmbeddingError:
                raise
            except Exception as exc:
                raise EmbeddingError(str(exc)) from exc

            for slot, vector in zip(miss_idx, raw, strict=True):
                normalized = _l2_normalize(vector)
                results[slot] = normalized
                await self._cache_set(self._cache_key(texts[slot]), normalized)

        return [r for r in results if r is not None]

    async def _cache_get(self, key: str) -> list[float] | None:
        if self._redis is None:
            return None
        try:
            raw = await self._redis.get(key)
        except Exception as exc:
            log.warning("embedding.cache_get_failed", error=str(exc))
            return None
        return json.loads(raw) if raw else None

    async def _cache_set(self, key: str, vector: list[float]) -> None:
        if self._redis is None:
            return
        try:
            await self._redis.set(key, json.dumps(vector), ex=self._settings.embedding_cache_ttl)
        except Exception as exc:
            log.warning("embedding.cache_set_failed", error=str(exc))
