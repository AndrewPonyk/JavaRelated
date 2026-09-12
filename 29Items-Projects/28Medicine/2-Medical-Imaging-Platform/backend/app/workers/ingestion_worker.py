"""Queue-driven worker for ingestion + ML inference jobs.

Runs the same image as the API with a different command (see docker-compose).
Consumes the configured queue (SQS in AWS, in-process in dev) and dispatches by
message type. Handlers are idempotent, so at-least-once delivery is safe.

Message shapes:
  {"type": "ingest",    "object_key": "staging/<uuid>.dcm"}
  {"type": "inference", "sop_instance_uid": "...", "object_key": "..."}
"""

from __future__ import annotations

import asyncio
from typing import Any

from app.core.config import settings
from app.core.logging import configure_logging, get_logger
from app.db.session import SessionFactory
from app.services.ingestion_service import IngestionService
from app.services.instance_service import InstanceService
from app.services.ml_service import MlService
from app.services.queue_service import get_queue_client
from app.services.storage_service import get_storage_service

log = get_logger(__name__)


async def process_message(message: dict[str, Any]) -> None:
    storage = get_storage_service()
    msg_type = message.get("type")

    if msg_type == "ingest":
        raw = storage.get_object(settings.s3_bucket_staging, message["object_key"])
        async with SessionFactory() as session:
            result = await IngestionService(session, storage).ingest(raw)
            await session.commit()
        log.info("worker.ingested", object_key=result.object_key)

    elif msg_type == "inference":
        async with SessionFactory() as session:
            instance = await InstanceService(session).get_by_sop_uid(message["sop_instance_uid"])
            await MlService(session).infer_and_store(instance)
            await session.commit()
        log.info("worker.inference_done", sop_instance_uid=message["sop_instance_uid"])

    else:
        log.warning("worker.unknown_message", type=msg_type)


async def run(poll_interval: float = 2.0) -> None:  # pragma: no cover - long-running
    configure_logging(settings.log_level, json_output=settings.is_production)
    queue = get_queue_client()
    log.info("worker.start", queue=settings.ingest_queue_name)
    while True:
        messages = queue.receive(max_messages=10)
        if not messages:
            await asyncio.sleep(poll_interval)
            continue
        for message in messages:
            try:
                await process_message(message)
            except Exception as exc:  # noqa: BLE001 - isolate poison messages
                log.error("worker.message_failed", error=str(exc))


if __name__ == "__main__":  # pragma: no cover
    asyncio.run(run())
