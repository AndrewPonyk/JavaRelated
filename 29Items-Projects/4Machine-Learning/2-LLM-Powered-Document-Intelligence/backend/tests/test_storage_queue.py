"""Unit tests for the storage abstraction and queue job serialization."""

from __future__ import annotations

import pytest

from app.queue import IngestionJob
from app.rag.errors import IngestionError
from app.storage import LocalStorage


@pytest.mark.asyncio
async def test_local_storage_put_get_delete(tmp_path) -> None:
    store = LocalStorage(str(tmp_path / "store"))
    await store.put("tenant-a/doc.txt", b"hello world")
    assert await store.get("tenant-a/doc.txt") == b"hello world"
    await store.delete("tenant-a/doc.txt")
    with pytest.raises(IngestionError):
        await store.get("tenant-a/doc.txt")


@pytest.mark.asyncio
async def test_local_storage_blocks_path_traversal(tmp_path) -> None:
    store = LocalStorage(str(tmp_path / "store"))
    with pytest.raises(IngestionError):
        await store.put("../escape.txt", b"x")


@pytest.mark.asyncio
async def test_local_storage_missing_key(tmp_path) -> None:
    store = LocalStorage(str(tmp_path / "store"))
    with pytest.raises(IngestionError):
        await store.get("nope.txt")


def test_ingestion_job_json_roundtrip() -> None:
    job = IngestionJob(
        document_id="d1", tenant_id="t1", s3_key="t1/a.txt", filename="a.txt", doc_type="legal"
    )
    restored = IngestionJob.from_json(job.to_json())
    assert restored == job
