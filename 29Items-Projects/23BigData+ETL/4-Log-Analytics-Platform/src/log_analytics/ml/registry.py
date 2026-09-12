"""Model registry: versioned persistence with a `latest` pointer.

Two implementations of one layout — local directory (dev/tests) and S3 (AWS, used by
EMR executors and the scheduled training job):

    <base>/<version>/model.joblib
    <base>/<version>/metadata.json
    <base>/latest.json                 → {"version": "..."}
"""

from __future__ import annotations

import json
import tempfile
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Protocol

from log_analytics.ml.isolation_forest import LogAnomalyDetector, ModelMetadata


class ModelRegistry(Protocol):
    def save(self, detector: LogAnomalyDetector, metadata: ModelMetadata) -> str: ...
    def load(self, version: str = "latest") -> tuple[LogAnomalyDetector, ModelMetadata]: ...
    def latest_version(self) -> str | None:
        """Cheap freshness probe — the scoring job polls this to hot-reload models."""
        ...


class LocalModelRegistry:
    def __init__(self, base_dir: str | Path) -> None:
        self._base = Path(base_dir)
        self._base.mkdir(parents=True, exist_ok=True)

    def save(self, detector: LogAnomalyDetector, metadata: ModelMetadata) -> str:
        version_dir = self._base / metadata.version
        version_dir.mkdir(parents=True, exist_ok=True)
        detector.save(version_dir / "model.joblib")
        (version_dir / "metadata.json").write_text(
            json.dumps(asdict(metadata), indent=2, default=str), encoding="utf-8"
        )
        (self._base / "latest.json").write_text(
            json.dumps({"version": metadata.version}), encoding="utf-8"
        )
        return metadata.version

    def load(self, version: str = "latest") -> tuple[LogAnomalyDetector, ModelMetadata]:
        if version == "latest":
            resolved = self.latest_version()
            if resolved is None:
                raise FileNotFoundError(
                    f"no models in registry at {self._base} — run: "
                    "python -m log_analytics.ml.train --source synthetic"
                )
            version = resolved
        version_dir = self._base / version
        detector = LogAnomalyDetector.load(version_dir / "model.joblib")
        metadata = ModelMetadata(
            **json.loads((version_dir / "metadata.json").read_text(encoding="utf-8"))
        )
        return detector, metadata

    def latest_version(self) -> str | None:
        pointer = self._base / "latest.json"
        if not pointer.exists():
            return None
        return str(json.loads(pointer.read_text(encoding="utf-8"))["version"])


class S3ModelRegistry:
    """Same layout over S3. `client` is injectable for tests (any boto3-shaped object)."""

    def __init__(self, uri: str, client: Any | None = None) -> None:
        if not uri.startswith("s3://"):
            raise ValueError(f"S3 registry URI must start with s3://, got {uri!r}")
        without_scheme = uri[len("s3://") :]
        bucket, _, prefix = without_scheme.partition("/")
        if not bucket:
            raise ValueError(f"S3 registry URI has no bucket: {uri!r}")
        self._bucket = bucket
        self._prefix = prefix.strip("/")
        if client is None:
            import boto3

            client = boto3.client("s3")
        self._s3 = client

    def _key(self, *parts: str) -> str:
        return "/".join(p for p in (self._prefix, *parts) if p)

    def save(self, detector: LogAnomalyDetector, metadata: ModelMetadata) -> str:
        with tempfile.TemporaryDirectory() as tmp:
            model_path = Path(tmp) / "model.joblib"
            detector.save(model_path)
            self._s3.put_object(
                Bucket=self._bucket,
                Key=self._key(metadata.version, "model.joblib"),
                Body=model_path.read_bytes(),
            )
        self._s3.put_object(
            Bucket=self._bucket,
            Key=self._key(metadata.version, "metadata.json"),
            Body=json.dumps(asdict(metadata), default=str).encode("utf-8"),
        )
        # latest.json written last: readers never see a pointer to a half-uploaded model.
        self._s3.put_object(
            Bucket=self._bucket,
            Key=self._key("latest.json"),
            Body=json.dumps({"version": metadata.version}).encode("utf-8"),
        )
        return metadata.version

    def load(self, version: str = "latest") -> tuple[LogAnomalyDetector, ModelMetadata]:
        if version == "latest":
            resolved = self.latest_version()
            if resolved is None:
                raise FileNotFoundError(f"no models under s3://{self._bucket}/{self._prefix}")
            version = resolved
        model_bytes = self._get(self._key(version, "model.joblib"))
        with tempfile.TemporaryDirectory() as tmp:
            model_path = Path(tmp) / "model.joblib"
            model_path.write_bytes(model_bytes)
            detector = LogAnomalyDetector.load(model_path)
        metadata = ModelMetadata(
            **json.loads(self._get(self._key(version, "metadata.json")).decode("utf-8"))
        )
        return detector, metadata

    def latest_version(self) -> str | None:
        from botocore.exceptions import ClientError

        try:
            body = self._get(self._key("latest.json"))
        except ClientError as exc:
            if exc.response.get("Error", {}).get("Code") in ("NoSuchKey", "404"):
                return None
            raise
        return str(json.loads(body.decode("utf-8"))["version"])

    def _get(self, key: str) -> bytes:
        resp = self._s3.get_object(Bucket=self._bucket, Key=key)
        return resp["Body"].read()


def get_registry(uri: str, s3_client: Any | None = None) -> ModelRegistry:
    if uri.startswith("s3://"):
        return S3ModelRegistry(uri, client=s3_client)
    return LocalModelRegistry(uri)


def new_version() -> str:
    """Sortable, human-readable version stamp, e.g. 20260707-153000."""
    return datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
