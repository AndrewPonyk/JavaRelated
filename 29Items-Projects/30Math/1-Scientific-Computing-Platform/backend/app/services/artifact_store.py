"""Computation artifact storage (plots, exports).

Two interchangeable backends behind one contract:

- ``S3ArtifactStore`` when ``S3_ARTIFACTS_BUCKET`` is configured (AWS envs) —
  serving redirects to short-lived pre-signed URLs;
- ``LocalArtifactStore`` otherwise (local dev, docker-compose, tests) — files
  under ``ARTIFACTS_DIR`` (a shared volume between api and worker containers),
  served through the authenticated artifact endpoint.

Workers write; the API serves. The stored reference travels inside
``result_payload["artifact"]``.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from app.core.config import get_settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ArtifactRef:
    storage: str  # "local" | "s3"
    key: str
    content_type: str

    def to_payload(self) -> dict:
        return {"storage": self.storage, "key": self.key, "content_type": self.content_type}

    @staticmethod
    def from_payload(payload: dict) -> ArtifactRef:
        return ArtifactRef(
            storage=str(payload["storage"]),
            key=str(payload["key"]),
            content_type=str(payload["content_type"]),
        )


class LocalArtifactStore:
    def __init__(self, base_dir: str) -> None:
        self._base = Path(base_dir).resolve()
        self._base.mkdir(parents=True, exist_ok=True)

    def save(self, computation_id: str, name: str, data: bytes, content_type: str) -> ArtifactRef:
        key = f"{computation_id}/{name}"
        path = self._safe_path(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return ArtifactRef(storage="local", key=key, content_type=content_type)

    def read(self, ref: ArtifactRef) -> bytes:
        return self._safe_path(ref.key).read_bytes()

    def _safe_path(self, key: str) -> Path:
        # Keys are server-generated (uuid/filename), but defend anyway:
        # a resolved path escaping the base directory is always an error.
        path = (self._base / key).resolve()
        if not path.is_relative_to(self._base):
            raise ValueError(f"Artifact key escapes the store: {key!r}")
        return path


class S3ArtifactStore:
    def __init__(self, bucket: str, region: str) -> None:
        self._bucket = bucket
        self._region = region

    def _client(self):
        import boto3

        return boto3.client("s3", region_name=self._region)

    def save(self, computation_id: str, name: str, data: bytes, content_type: str) -> ArtifactRef:
        key = f"computations/{computation_id}/{name}"
        self._client().put_object(Bucket=self._bucket, Key=key, Body=data, ContentType=content_type)
        return ArtifactRef(storage="s3", key=key, content_type=content_type)

    def presigned_url(self, ref: ArtifactRef, *, ttl_seconds: int = 300) -> str:
        return self._client().generate_presigned_url(
            "get_object",
            Params={"Bucket": self._bucket, "Key": ref.key},
            ExpiresIn=ttl_seconds,
        )


@lru_cache
def get_artifact_store() -> LocalArtifactStore | S3ArtifactStore:
    settings = get_settings()
    if settings.s3_artifacts_bucket:
        return S3ArtifactStore(settings.s3_artifacts_bucket, settings.aws_region)
    return LocalArtifactStore(settings.artifacts_dir)
