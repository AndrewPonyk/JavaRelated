"""Data access for documents + chunks, and keyword search.

Keyword search is dialect-aware: on **PostgreSQL** it uses full-text search
(``to_tsvector`` / ``websearch_to_tsquery`` / ``ts_rank``), which hits the GIN index created
in the migration; on **SQLite** (dev/tests) it falls back to a portable LIKE + term-frequency
score. List/count operations use aggregates so chunk rows (and their text) are never loaded
just to produce a count.
"""

from __future__ import annotations

import re

from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.document import Document, DocumentChunk
from app.vectorstores.base import SearchHit

_TOKEN_RE = re.compile(r"[a-z0-9]+")


def _terms(text: str) -> list[str]:
    return [t for t in _TOKEN_RE.findall(text.lower()) if len(t) > 1]


class DocumentRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    def _dialect(self) -> str:
        bind = self._session.bind
        return getattr(getattr(bind, "dialect", None), "name", "") or ""

    async def create(self, document: Document) -> Document:
        self._session.add(document)
        await self._session.flush()
        return document

    async def get(self, document_id: str) -> Document | None:
        return await self._session.get(Document, document_id)

    async def count_chunks(self, document_id: str) -> int:
        return int(
            await self._session.scalar(
                select(func.count())
                .select_from(DocumentChunk)
                .where(DocumentChunk.document_id == document_id)
            )
            or 0
        )

    async def chunk_ids(self, document_id: str) -> list[str]:
        rows = await self._session.scalars(
            select(DocumentChunk.id).where(DocumentChunk.document_id == document_id)
        )
        return list(rows)

    async def list_with_counts(
        self, *, limit: int = 50, offset: int = 0
    ) -> tuple[list[tuple[Document, int]], int]:
        """Return (document, chunk_count) pairs + total, without loading chunk rows."""
        stmt = (
            select(Document, func.count(DocumentChunk.id))
            .outerjoin(DocumentChunk, DocumentChunk.document_id == Document.id)
            .group_by(Document.id)
            .order_by(Document.created_at.desc())
            .limit(limit)
            .offset(offset)
        )
        rows = (await self._session.execute(stmt)).all()
        total = await self._session.scalar(select(func.count()).select_from(Document)) or 0
        return [(doc, int(count)) for doc, count in rows], int(total)

    async def delete(self, document_id: str) -> bool:
        document = await self._session.get(Document, document_id)
        if document is None:
            return False
        # DB-level ON DELETE CASCADE removes chunks (FK enforced on Postgres; PRAGMA on SQLite).
        await self._session.delete(document)
        # Flush so the DELETE is emitted before any same-PK re-insert (used by update()).
        await self._session.flush()
        return True

    async def keyword_search(
        self, query: str, k: int = 10, *, candidate_limit: int = 500
    ) -> list[SearchHit]:
        if not _terms(query):
            return []
        if self._dialect() == "postgresql":
            return await self._keyword_search_fts(query, k)
        return await self._keyword_search_like(query, k, candidate_limit)

    async def _keyword_search_fts(self, query: str, k: int) -> list[SearchHit]:
        tsv = func.to_tsvector("english", DocumentChunk.text)
        tsq = func.websearch_to_tsquery("english", query)
        rank = func.ts_rank(tsv, tsq)
        stmt = (
            select(DocumentChunk, rank.label("rank"))
            .where(tsv.op("@@")(tsq))
            .order_by(rank.desc())
            .limit(k)
        )
        rows = (await self._session.execute(stmt)).all()
        return [self._to_hit(chunk, float(score)) for chunk, score in rows]

    async def _keyword_search_like(
        self, query: str, k: int, candidate_limit: int
    ) -> list[SearchHit]:
        terms = _terms(query)
        conditions = [func.lower(DocumentChunk.text).like(f"%{term}%") for term in terms]
        rows = await self._session.scalars(
            select(DocumentChunk).where(or_(*conditions)).limit(candidate_limit)
        )
        hits: list[SearchHit] = []
        for chunk in rows:
            haystack = chunk.text.lower()
            score = float(sum(haystack.count(term) for term in terms))
            if score > 0:
                hits.append(self._to_hit(chunk, score))
        hits.sort(key=lambda h: h.score, reverse=True)
        return hits[:k]

    @staticmethod
    def _to_hit(chunk: DocumentChunk, score: float) -> SearchHit:
        return SearchHit(
            id=chunk.id,
            score=score,
            text=chunk.text,
            metadata={
                **(chunk.meta or {}),
                "document_id": chunk.document_id,
                "chunk_index": chunk.chunk_index,
            },
        )
