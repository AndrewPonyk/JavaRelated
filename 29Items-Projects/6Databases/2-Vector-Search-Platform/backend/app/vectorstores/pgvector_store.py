"""pgvector backend — the reference implementation.

PostgreSQL + the ``vector`` extension with an HNSW cosine index. Uses an asyncpg pool
directly (fast, simple for vector ops). Requires the optional ``asyncpg`` + ``pgvector``
packages; imports are guarded so the app still boots without them.
"""

from __future__ import annotations

import json
from typing import Any

import structlog

from app.core.exceptions import BackendUnavailableError
from app.vectorstores.base import SearchHit, VectorRecord, VectorStore

log = structlog.get_logger(__name__)

try:  # optional deps — only needed when this backend is actually used
    import asyncpg
    from pgvector.asyncpg import register_vector

    _PG_AVAILABLE = True
except ImportError:  # pragma: no cover - exercised only where deps are missing
    _PG_AVAILABLE = False


def _to_asyncpg_dsn(url: str) -> str:
    # SQLAlchemy-style "postgresql+asyncpg://" -> plain "postgresql://" for asyncpg.
    return url.replace("postgresql+asyncpg://", "postgresql://").replace(
        "postgres+asyncpg://", "postgresql://"
    )


class PgVectorStore(VectorStore):
    name = "pgvector"

    def __init__(self, dsn: str, *, dim: int, table: str = "chunk_embeddings") -> None:
        self._dsn = _to_asyncpg_dsn(dsn)
        self._dim = dim
        self._table = table
        self._pool: Any | None = None

    async def _init_conn(self, conn: Any) -> None:
        await register_vector(conn)

    async def ensure_ready(self) -> None:
        if not _PG_AVAILABLE:
            raise BackendUnavailableError(
                "pgvector backend needs 'asyncpg' and 'pgvector' installed."
            )
        if self._pool is not None:
            return
        try:
            # The extension must exist BEFORE register_vector() (pool init) runs, otherwise
            # asyncpg can't resolve the `vector` type. Create it on a plain connection first.
            bootstrap = await asyncpg.connect(self._dsn)
            try:
                await bootstrap.execute("CREATE EXTENSION IF NOT EXISTS vector")
            finally:
                await bootstrap.close()

            self._pool = await asyncpg.create_pool(
                self._dsn, min_size=1, max_size=10, init=self._init_conn
            )
            async with self._pool.acquire() as conn:
                await conn.execute(
                    f"""
                    CREATE TABLE IF NOT EXISTS {self._table} (
                        id           TEXT PRIMARY KEY,
                        document_id  TEXT NOT NULL,
                        chunk_index  INT  NOT NULL DEFAULT 0,
                        text         TEXT NOT NULL,
                        metadata     JSONB NOT NULL DEFAULT '{{}}',
                        embedding    vector({self._dim}) NOT NULL
                    )
                    """
                )
                await conn.execute(
                    f"CREATE INDEX IF NOT EXISTS {self._table}_hnsw ON {self._table} "
                    f"USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)"
                )
        except BackendUnavailableError:
            raise
        except Exception as exc:
            raise BackendUnavailableError(f"pgvector connection failed: {exc}") from exc

    async def upsert(self, records: list[VectorRecord]) -> int:
        pool = self._require_pool()
        rows = [
            (
                r.id,
                str(r.metadata.get("document_id", r.id.split(":")[0])),
                int(r.metadata.get("chunk_index", 0)),
                r.text,
                json.dumps(r.metadata),
                r.vector,
            )
            for r in records
        ]
        async with pool.acquire() as conn:
            await conn.executemany(
                f"""
                INSERT INTO {self._table} (id, document_id, chunk_index, text, metadata, embedding)
                VALUES ($1, $2, $3, $4, $5::jsonb, $6)
                ON CONFLICT (id) DO UPDATE SET
                    document_id = EXCLUDED.document_id,
                    chunk_index = EXCLUDED.chunk_index,
                    text = EXCLUDED.text,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding
                """,
                rows,
            )
        return len(rows)

    async def query(
        self,
        vector: list[float],
        k: int = 10,
        *,
        filters: dict[str, Any] | None = None,
    ) -> list[SearchHit]:
        pool = self._require_pool()
        where = ""
        params: list[Any] = [vector, k]
        if filters:
            where = "WHERE metadata @> $3::jsonb"
            params.append(json.dumps(filters))
        sql = (
            f"SELECT id, text, metadata, 1 - (embedding <=> $1) AS score "
            f"FROM {self._table} {where} ORDER BY embedding <=> $1 LIMIT $2"
        )
        async with pool.acquire() as conn:
            rows = await conn.fetch(sql, *params)
        return [
            SearchHit(
                id=row["id"],
                score=float(row["score"]),
                text=row["text"],
                metadata=json.loads(row["metadata"])
                if isinstance(row["metadata"], str)
                else dict(row["metadata"]),
            )
            for row in rows
        ]

    async def delete(self, ids: list[str]) -> int:
        pool = self._require_pool()
        async with pool.acquire() as conn:
            result = await conn.execute(
                f"DELETE FROM {self._table} WHERE id = ANY($1::text[])", ids
            )
        # result like "DELETE <n>"
        try:
            return int(result.split()[-1])
        except (ValueError, IndexError):
            return 0

    async def count(self) -> int:
        pool = self._require_pool()
        async with pool.acquire() as conn:
            return int(await conn.fetchval(f"SELECT count(*) FROM {self._table}"))

    async def close(self) -> None:
        if self._pool is not None:
            await self._pool.close()
            self._pool = None

    def _require_pool(self) -> Any:
        if self._pool is None:
            raise BackendUnavailableError("pgvector pool not initialized; call ensure_ready().")
        return self._pool
