"""Pipeline registry CRUD — service layer over pluggable persistence.

Backends (PIPELINE_STORE):
    redis      — local/dev default; survives API restarts in the compose stack
    snowflake  — RAW.METADATA.PIPELINES (migrations/V1.2.0) for cloud envs

Name uniqueness: Snowflake does not enforce UNIQUE constraints, so the service
checks-then-writes in both backends; the Redis backend additionally guards the
insert with HSETNX on the name index.
"""

from __future__ import annotations

import asyncio
import uuid
from datetime import UTC, datetime
from typing import Protocol

import redis.asyncio as aioredis

from app.config import get_settings
from app.schemas.pipeline import PipelineCreate, PipelineRead, PipelineStatus, PipelineUpdate


class PipelineNotFoundError(KeyError):
    pass


class DuplicatePipelineNameError(ValueError):
    pass


class PipelineRepository(Protocol):
    async def list(self) -> list[PipelineRead]: ...
    async def get(self, pipeline_id: str) -> PipelineRead | None: ...
    async def get_by_name(self, name: str) -> PipelineRead | None: ...
    async def insert(self, pipeline: PipelineRead) -> None: ...
    async def replace(self, old_name: str, pipeline: PipelineRead) -> None: ...
    async def delete(self, pipeline: PipelineRead) -> None: ...


# ── Redis backend ────────────────────────────────────────────────────────────

ITEMS_KEY = "pipelines:items"  # hash: id → PipelineRead json
NAMES_KEY = "pipelines:names"  # hash: name → id (uniqueness index)


class RedisPipelineRepository:
    def __init__(self, redis_url: str | None = None, client: aioredis.Redis | None = None) -> None:
        self._redis_url = redis_url or get_settings().redis_url
        self._redis: aioredis.Redis | None = client

    def _client(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.from_url(self._redis_url, decode_responses=True)
        return self._redis

    async def list(self) -> list[PipelineRead]:
        raw = await self._client().hvals(ITEMS_KEY)
        items = [PipelineRead.model_validate_json(value) for value in raw]
        return sorted(items, key=lambda p: p.created_at)

    async def get(self, pipeline_id: str) -> PipelineRead | None:
        raw = await self._client().hget(ITEMS_KEY, pipeline_id)
        return PipelineRead.model_validate_json(raw) if raw else None

    async def get_by_name(self, name: str) -> PipelineRead | None:
        pipeline_id = await self._client().hget(NAMES_KEY, name)
        return await self.get(pipeline_id) if pipeline_id else None

    async def insert(self, pipeline: PipelineRead) -> None:
        client = self._client()
        claimed = await client.hsetnx(NAMES_KEY, pipeline.name, pipeline.id)
        if not claimed:
            raise DuplicatePipelineNameError(pipeline.name)
        await client.hset(ITEMS_KEY, pipeline.id, pipeline.model_dump_json())

    async def replace(self, old_name: str, pipeline: PipelineRead) -> None:
        client = self._client()
        if pipeline.name != old_name:
            claimed = await client.hsetnx(NAMES_KEY, pipeline.name, pipeline.id)
            if not claimed:
                raise DuplicatePipelineNameError(pipeline.name)
            await client.hdel(NAMES_KEY, old_name)
        await client.hset(ITEMS_KEY, pipeline.id, pipeline.model_dump_json())

    async def delete(self, pipeline: PipelineRead) -> None:
        client = self._client()
        await client.hdel(ITEMS_KEY, pipeline.id)
        await client.hdel(NAMES_KEY, pipeline.name)


# ── Snowflake backend ────────────────────────────────────────────────────────

_COLUMNS = (
    "id, name, description, schedule, source, target, transform_ref, "
    "status, created_at, updated_at"
)
_TABLE = "raw.metadata.pipelines"


class SnowflakePipelineRepository:
    """Backed by RAW.METADATA.PIPELINES; sync connector calls run in a thread."""

    def _row(self, row: tuple) -> PipelineRead:
        return PipelineRead(
            id=row[0],
            name=row[1],
            description=row[2] or "",
            schedule=row[3],
            source=row[4],
            target=row[5],
            transform_ref=row[6] or "",
            status=PipelineStatus(row[7]),
            created_at=row[8],
            updated_at=row[9],
        )

    async def list(self) -> list[PipelineRead]:
        from app.db import snowflake_client

        rows = await asyncio.to_thread(
            snowflake_client.fetch_all,
            f"select {_COLUMNS} from {_TABLE} order by created_at",
        )
        return [self._row(row) for row in rows]

    async def get(self, pipeline_id: str) -> PipelineRead | None:
        from app.db import snowflake_client

        rows = await asyncio.to_thread(
            snowflake_client.fetch_all,
            f"select {_COLUMNS} from {_TABLE} where id = %s",
            (pipeline_id,),
        )
        return self._row(rows[0]) if rows else None

    async def get_by_name(self, name: str) -> PipelineRead | None:
        from app.db import snowflake_client

        rows = await asyncio.to_thread(
            snowflake_client.fetch_all,
            f"select {_COLUMNS} from {_TABLE} where name = %s",
            (name,),
        )
        return self._row(rows[0]) if rows else None

    async def insert(self, pipeline: PipelineRead) -> None:
        from app.db import snowflake_client

        await asyncio.to_thread(
            snowflake_client.execute,
            f"insert into {_TABLE} ({_COLUMNS}) " "values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
            (
                pipeline.id,
                pipeline.name,
                pipeline.description,
                pipeline.schedule,
                pipeline.source,
                pipeline.target,
                pipeline.transform_ref,
                pipeline.status.value,
                pipeline.created_at,
                pipeline.updated_at,
            ),
        )

    async def replace(self, old_name: str, pipeline: PipelineRead) -> None:
        from app.db import snowflake_client

        await asyncio.to_thread(
            snowflake_client.execute,
            f"update {_TABLE} set name = %s, description = %s, schedule = %s, "
            "source = %s, target = %s, transform_ref = %s, status = %s, "
            "updated_at = %s where id = %s",
            (
                pipeline.name,
                pipeline.description,
                pipeline.schedule,
                pipeline.source,
                pipeline.target,
                pipeline.transform_ref,
                pipeline.status.value,
                pipeline.updated_at,
                pipeline.id,
            ),
        )

    async def delete(self, pipeline: PipelineRead) -> None:
        from app.db import snowflake_client

        await asyncio.to_thread(
            snowflake_client.execute, f"delete from {_TABLE} where id = %s", (pipeline.id,)
        )


# ── Service ──────────────────────────────────────────────────────────────────


class PipelineService:
    def __init__(self, repository: PipelineRepository) -> None:
        self._repo = repository

    async def list(self) -> list[PipelineRead]:
        return await self._repo.list()

    async def get(self, pipeline_id: str) -> PipelineRead:
        pipeline = await self._repo.get(pipeline_id)
        if pipeline is None:
            raise PipelineNotFoundError(pipeline_id)
        return pipeline

    async def create(self, payload: PipelineCreate) -> PipelineRead:
        if await self._repo.get_by_name(payload.name) is not None:
            raise DuplicatePipelineNameError(payload.name)
        now = datetime.now(UTC)
        pipeline = PipelineRead(
            id=uuid.uuid4().hex,
            status=PipelineStatus.draft,
            created_at=now,
            updated_at=now,
            **payload.model_dump(),
        )
        await self._repo.insert(pipeline)
        return pipeline

    async def update(self, pipeline_id: str, payload: PipelineUpdate) -> PipelineRead:
        current = await self.get(pipeline_id)
        changes = payload.model_dump(exclude_unset=True, exclude_none=True)
        new_name = changes.get("name")
        if new_name and new_name != current.name:
            existing = await self._repo.get_by_name(new_name)
            if existing is not None and existing.id != pipeline_id:
                raise DuplicatePipelineNameError(new_name)
        updated = current.model_copy(update={**changes, "updated_at": datetime.now(UTC)})
        await self._repo.replace(current.name, updated)
        return updated

    async def delete(self, pipeline_id: str) -> None:
        pipeline = await self.get(pipeline_id)
        await self._repo.delete(pipeline)


_service: PipelineService | None = None


def get_pipeline_service() -> PipelineService:
    global _service
    if _service is None:
        settings = get_settings()
        repo: PipelineRepository
        if settings.pipeline_store == "snowflake":
            repo = SnowflakePipelineRepository()
        else:
            repo = RedisPipelineRepository()
        _service = PipelineService(repo)
    return _service
