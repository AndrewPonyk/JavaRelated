"""Object storage abstraction (raw document bytes).

``local`` writes under a data directory (dev/docker); ``s3`` uses boto3 (production).
Selected by ``settings.storage_backend``.
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Protocol

from app.core.config import settings
from app.rag.errors import IngestionError


class Storage(Protocol):
    async def put(self, key: str, content: bytes) -> str: ...

    async def get(self, key: str) -> bytes: ...

    async def delete(self, key: str) -> None: ...


class LocalStorage:
    """Filesystem-backed storage rooted at ``settings.local_storage_dir``."""

    def __init__(self, root: str) -> None:
        self._root = Path(root)
        self._root.mkdir(parents=True, exist_ok=True)

    def _path(self, key: str) -> Path:
        # Prevent path traversal — keys are tenant/filename, never absolute or "..".
        safe = Path(key)
        if safe.is_absolute() or ".." in safe.parts:
            raise IngestionError(f"unsafe storage key: {key!r}")
        return self._root / safe

    async def put(self, key: str, content: bytes) -> str:
        path = self._path(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return key

    async def get(self, key: str) -> bytes:
        path = self._path(key)
        if not path.exists():
            raise IngestionError(f"object not found: {key!r}")
        return path.read_bytes()

    async def delete(self, key: str) -> None:
        path = self._path(key)
        path.unlink(missing_ok=True)


class S3Storage:
    """Amazon S3-backed storage (boto3)."""

    def __init__(self, bucket: str, region: str) -> None:
        import boto3  # imported here so local/test runs don't require AWS config

        self._bucket = bucket
        self._client = boto3.client("s3", region_name=region)

    async def put(self, key: str, content: bytes) -> str:
        self._client.put_object(Bucket=self._bucket, Key=key, Body=content)
        return key

    async def get(self, key: str) -> bytes:
        obj = self._client.get_object(Bucket=self._bucket, Key=key)
        return obj["Body"].read()

    async def delete(self, key: str) -> None:
        self._client.delete_object(Bucket=self._bucket, Key=key)


@lru_cache
def get_storage() -> Storage:
    if settings.storage_backend == "s3":
        return S3Storage(settings.s3_bucket, settings.aws_region)
    return LocalStorage(settings.local_storage_dir)
