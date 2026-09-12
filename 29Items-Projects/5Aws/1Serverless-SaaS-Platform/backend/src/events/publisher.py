import json
from datetime import datetime, timezone
from typing import Any

import boto3

from src.core.config import get_settings
from src.core.logger import get_logger

logger = get_logger("event-publisher")
_events_client: Any = None


def get_events_client() -> Any:
    global _events_client
    settings = get_settings()
    if _events_client is None:
        _events_client = boto3.client("events", region_name=settings.aws_region)
    return _events_client


def publish_domain_event(detail_type: str, detail: dict[str, Any], source: str = "saas.usage") -> str:
    """Publishes an event to the custom EventBridge bus."""
    settings = get_settings()
    client = get_events_client()

    entry = {
        "Time": datetime.now(timezone.utc),
        "Source": source,
        "DetailType": detail_type,
        "Detail": json.dumps(detail),
        "EventBusName": settings.event_bus_name,
    }

    response = client.put_events(Entries=[entry])
    failed_count = response.get("FailedEntryCount", 0)

    if failed_count > 0:
        error_msg = response["Entries"][0].get("ErrorMessage", "Unknown EventBridge error")
        logger.error("Failed to publish domain event", detail_type=detail_type, error=error_msg)
        raise RuntimeError(f"EventBridge publication failed: {error_msg}")

    event_id = response["Entries"][0]["EventId"]
    logger.info("Successfully published domain event", detail_type=detail_type, event_id=event_id)
    return str(event_id)
