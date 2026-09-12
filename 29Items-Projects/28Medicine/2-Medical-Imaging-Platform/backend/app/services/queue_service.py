"""Queue abstraction: SQS in AWS, in-process deque for local dev / tests.

Decouples the request path from the ingestion/ML workers. The same JSON message
shape flows through both backends so the worker code is backend-agnostic.
"""

from __future__ import annotations

import json
from collections import deque
from typing import Any, Protocol

import boto3
from botocore.exceptions import BotoCoreError, ClientError

from app.core.config import settings
from app.core.exceptions import DomainError
from app.core.logging import get_logger

log = get_logger(__name__)


class QueueClient(Protocol):
    def send(self, message: dict[str, Any]) -> None:
        ...

    def receive(self, max_messages: int = 1) -> list[dict[str, Any]]:
        ...


class InProcessQueue:
    """Thread-naive in-memory queue for single-process dev and tests."""

    def __init__(self) -> None:
        self._q: deque[dict[str, Any]] = deque()

    def send(self, message: dict[str, Any]) -> None:
        self._q.append(message)
        log.info("queue.send.inproc", depth=len(self._q))

    def receive(self, max_messages: int = 1) -> list[dict[str, Any]]:
        out: list[dict[str, Any]] = []
        while self._q and len(out) < max_messages:
            out.append(self._q.popleft())
        return out

    def __len__(self) -> int:
        return len(self._q)


class SqsQueue:
    """AWS SQS-backed queue."""

    def __init__(self, queue_url: str) -> None:
        self._url = queue_url
        self._client = boto3.client("sqs", region_name=settings.s3_region)

    def send(self, message: dict[str, Any]) -> None:
        try:
            self._client.send_message(QueueUrl=self._url, MessageBody=json.dumps(message))
            log.info("queue.send.sqs")
        except (ClientError, BotoCoreError) as exc:  # pragma: no cover
            raise DomainError("Failed to enqueue message") from exc

    def receive(self, max_messages: int = 1) -> list[dict[str, Any]]:  # pragma: no cover
        resp = self._client.receive_message(
            QueueUrl=self._url, MaxNumberOfMessages=max_messages, WaitTimeSeconds=20
        )
        messages = []
        for m in resp.get("Messages", []):
            messages.append(json.loads(m["Body"]))
            self._client.delete_message(QueueUrl=self._url, ReceiptHandle=m["ReceiptHandle"])
        return messages


_queue: QueueClient | None = None


def get_queue_client() -> QueueClient:
    """Return the configured queue backend (SQS if QUEUE_URL set, else in-proc)."""
    global _queue
    if _queue is None:
        _queue = SqsQueue(settings.queue_url) if settings.queue_url else InProcessQueue()
    return _queue
