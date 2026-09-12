"""Ingestion job queue.

In ``queue`` mode (production) jobs go to SQS and the worker (:mod:`app.worker`) consumes
them. In ``inline`` mode (local/dev) the service ingests synchronously on upload, so the
queue is unused. Selected by ``settings.ingest_mode``.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from functools import lru_cache
from typing import Protocol

from app.core.config import settings


@dataclass(frozen=True)
class IngestionJob:
    document_id: str
    tenant_id: str
    s3_key: str
    filename: str
    doc_type: str

    def to_json(self) -> str:
        return json.dumps(asdict(self))

    @classmethod
    def from_json(cls, raw: str) -> IngestionJob:
        return cls(**json.loads(raw))


class IngestionQueue(Protocol):
    def enqueue(self, job: IngestionJob) -> None: ...


class SqsQueue:
    """Amazon SQS-backed queue (boto3)."""

    def __init__(self, queue_url: str, region: str) -> None:
        import boto3

        self._url = queue_url
        self._client = boto3.client("sqs", region_name=region)

    def enqueue(self, job: IngestionJob) -> None:
        self._client.send_message(QueueUrl=self._url, MessageBody=job.to_json())

    def receive(
        self, max_messages: int = 5, wait_seconds: int = 20
    ) -> list[tuple[str, IngestionJob]]:
        """Long-poll for jobs. Returns (receipt_handle, job) tuples."""
        resp = self._client.receive_message(
            QueueUrl=self._url,
            MaxNumberOfMessages=max_messages,
            WaitTimeSeconds=wait_seconds,
        )
        return [
            (m["ReceiptHandle"], IngestionJob.from_json(m["Body"]))
            for m in resp.get("Messages", [])
        ]

    def ack(self, receipt_handle: str) -> None:
        self._client.delete_message(QueueUrl=self._url, ReceiptHandle=receipt_handle)


@lru_cache
def get_queue() -> SqsQueue:
    if not settings.sqs_queue_url:
        raise RuntimeError("SQS_QUEUE_URL is not configured (required for ingest_mode=queue)")
    return SqsQueue(settings.sqs_queue_url, settings.aws_region)
