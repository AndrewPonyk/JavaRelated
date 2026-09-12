"""Object storage abstraction over the S3 API (MinIO locally, S3 in AWS).

Targets the S3 API so the same code works against MinIO and AWS — switch by
setting `S3_ENDPOINT_URL`. The API brokers *presigned URLs* for large objects
rather than proxying bytes (offloads bandwidth, keeps access time-boxed).

Encryption: real S3 gets SSE-KMS (HIPAA at-rest). MinIO (dev) does not accept
the `aws:kms` header, so SSE is omitted there — MinIO handles at-rest encryption
via its own server configuration.
"""

from __future__ import annotations

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError

from app.core.config import settings
from app.core.exceptions import NotFoundError, StorageError
from app.core.logging import get_logger

log = get_logger(__name__)


class StorageService:
    def __init__(self) -> None:
        self._is_real_s3 = settings.s3_endpoint_url is None
        self._client = boto3.client(
            "s3",
            endpoint_url=settings.s3_endpoint_url,  # None → real AWS S3
            region_name=settings.s3_region,
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
            use_ssl=settings.s3_use_ssl,
            config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
        )

    # ── bucket management ────────────────────────────────────
    def ensure_buckets(self) -> None:
        """Create archive + staging buckets if missing (idempotent)."""
        for bucket in (settings.s3_bucket_staging, settings.s3_bucket_dicom):
            try:
                self._client.head_bucket(Bucket=bucket)
            except ClientError:
                log.info("storage.create_bucket", bucket=bucket)
                try:
                    self._client.create_bucket(Bucket=bucket)
                except (ClientError, BotoCoreError) as exc:  # pragma: no cover
                    raise StorageError(f"create_bucket failed for {bucket}") from exc

    # ── object I/O ───────────────────────────────────────────
    def put_object(self, bucket: str, key: str, body: bytes, content_type: str) -> None:
        extra = {}
        if self._is_real_s3:
            extra["ServerSideEncryption"] = "aws:kms"  # encryption at rest (HIPAA)
        try:
            self._client.put_object(
                Bucket=bucket, Key=key, Body=body, ContentType=content_type, **extra
            )
        except (ClientError, BotoCoreError) as exc:
            raise StorageError(f"put_object failed for {key}") from exc

    def get_object(self, bucket: str, key: str) -> bytes:
        try:
            resp = self._client.get_object(Bucket=bucket, Key=key)
            return resp["Body"].read()
        except ClientError as exc:
            if exc.response.get("Error", {}).get("Code") in ("NoSuchKey", "404"):
                raise NotFoundError(f"Object not found: {key}") from exc
            raise StorageError(f"get_object failed for {key}") from exc
        except BotoCoreError as exc:
            raise StorageError(f"get_object failed for {key}") from exc

    def object_exists(self, bucket: str, key: str) -> bool:
        try:
            self._client.head_object(Bucket=bucket, Key=key)
            return True
        except ClientError:
            return False

    def delete_object(self, bucket: str, key: str) -> None:
        try:
            self._client.delete_object(Bucket=bucket, Key=key)
        except (ClientError, BotoCoreError) as exc:  # pragma: no cover
            raise StorageError(f"delete_object failed for {key}") from exc

    def presigned_get(self, bucket: str, key: str, expires: int | None = None) -> str:
        """Time-boxed GET URL for direct browser/WADO retrieval."""
        try:
            return self._client.generate_presigned_url(
                "get_object",
                Params={"Bucket": bucket, "Key": key},
                ExpiresIn=expires or settings.presign_expiry_seconds,
            )
        except (ClientError, BotoCoreError) as exc:  # pragma: no cover
            raise StorageError("presign failed") from exc


# Module-level singleton accessor (overridable in tests via DI).
_storage: StorageService | None = None


def get_storage_service() -> StorageService:
    global _storage
    if _storage is None:
        _storage = StorageService()
    return _storage
