"""Search orchestration: vector, keyword, and hybrid (RRF) — backend-agnostic.

The service asks the factory for a ``VectorStore`` and the repository for keyword hits; it
never imports a concrete backend. Hybrid fusion uses Reciprocal Rank Fusion, which combines
rankings without needing the two score scales to be comparable. Optional lexical reranking is
applied when ``ENABLE_RERANKER`` is set.
"""

from __future__ import annotations

import structlog

from app.core.config import Backend, Settings, get_settings
from app.repositories.document_repository import DocumentRepository
from app.schemas.search import SearchMode, SearchResult
from app.services.embedding_service import EmbeddingService
from app.services.reranker import lexical_rerank
from app.vectorstores import SearchHit, VectorStore, get_vector_store

log = structlog.get_logger(__name__)

# RRF constant; 60 is the value from the original Cormack et al. paper and a sane default.
_RRF_K = 60


def reciprocal_rank_fusion(rankings: list[list[SearchHit]], *, k: int = _RRF_K) -> list[SearchHit]:
    """Fuse multiple ranked lists into one by summing 1 / (k + rank)."""
    scores: dict[str, float] = {}
    hit_by_id: dict[str, SearchHit] = {}
    for ranking in rankings:
        for rank, hit in enumerate(ranking):
            scores[hit.id] = scores.get(hit.id, 0.0) + 1.0 / (k + rank + 1)
            hit_by_id.setdefault(hit.id, hit)
    fused = [
        SearchHit(id=hid, score=score, text=hit_by_id[hid].text, metadata=hit_by_id[hid].metadata)
        for hid, score in scores.items()
    ]
    fused.sort(key=lambda h: h.score, reverse=True)
    return fused


class SearchService:
    def __init__(
        self,
        embedder: EmbeddingService,
        repository: DocumentRepository | None = None,
        settings: Settings | None = None,
    ) -> None:
        self._settings = settings or get_settings()
        self._embedder = embedder
        self._repo = repository

    async def search(
        self,
        query: str,
        *,
        k: int = 10,
        backend: Backend | str | None = None,
        mode: SearchMode = SearchMode.hybrid,
        filters: dict | None = None,
    ) -> list[SearchResult]:
        store: VectorStore = get_vector_store(backend)
        await store.ensure_ready()

        if mode is SearchMode.keyword:
            hits = await self._keyword_search(query, k)
        elif mode is SearchMode.vector:
            hits = await self._vector_search(store, query, k, filters)
        else:  # hybrid: fan out wider, then fuse and truncate to k.
            fanout = k * 3
            vector_hits = await self._vector_search(store, query, fanout, filters)
            keyword_hits = await self._keyword_search(query, fanout)
            hits = reciprocal_rank_fusion([vector_hits, keyword_hits])[:k]

        if self._settings.enable_reranker:
            hits = lexical_rerank(query, hits)[:k]

        return [
            SearchResult(id=h.id, score=round(h.score, 6), text=h.text, metadata=h.metadata)
            for h in hits
        ]

    async def _vector_search(
        self, store: VectorStore, query: str, k: int, filters: dict | None
    ) -> list[SearchHit]:
        query_vector = await self._embedder.embed(query)
        return await store.query(query_vector, k=k, filters=filters)

    async def _keyword_search(self, query: str, k: int) -> list[SearchHit]:
        if self._repo is None:
            return []
        return await self._repo.keyword_search(
            query, k, candidate_limit=self._settings.keyword_candidate_limit
        )
