import asyncio
import json
import logging

from aiokafka import AIOKafkaConsumer
from pydantic import ValidationError

from app.core.config import settings
from app.db.session import SessionLocal
from app.schemas import TelemetryCreate
from app.services.fleet_service import FleetService

logger = logging.getLogger(__name__)


async def consume_telemetry() -> None:
    """Consume raw vehicle telemetry events and persist normalized fleet state."""
    consumer = AIOKafkaConsumer(
        "vehicle.telemetry.received",
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id="fleet-telemetry-consumer",
        value_deserializer=lambda value: json.loads(value.decode("utf-8")),
    )
    await consumer.start()
    try:
        async for message in consumer:
            try:
                payload = TelemetryCreate.model_validate(message.value)
                with SessionLocal() as db:
                    FleetService(db).ingest_telemetry(payload)
                logger.info(
                    "telemetry persisted",
                    extra={"offset": message.offset, "vehicle_id": payload.vehicle_id},
                )
            except ValidationError as exc:
                logger.warning("invalid telemetry event", extra={"offset": message.offset, "error": str(exc)})
            except Exception as exc:
                logger.exception("telemetry event failed", extra={"offset": message.offset, "error": str(exc)})
    finally:
        await consumer.stop()


if __name__ == "__main__":
    asyncio.run(consume_telemetry())
