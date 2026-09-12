"""Server-sent events: live anomaly notifications from Redis pub/sub.

Consume with fetch-streams (headers supported) or any SSE client:
    GET /api/v1/events/anomalies?device_id=dev-xxx
Events are the JSON payloads published by the anomaly worker on the
`anomalies:{device_id}` channel; a comment line is emitted every 15s as
keep-alive.
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from typing import Any

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.api.deps import get_current_user
from app.db import redis

logger = logging.getLogger(__name__)

router = APIRouter(dependencies=[Depends(get_current_user)])

KEEPALIVE_SECONDS = 15.0


async def event_stream(pubsub: Any, channel: str) -> AsyncIterator[str]:
    """Yield SSE frames from a subscribed pubsub; tears down on disconnect.

    Separated from the endpoint so the streaming contract (frames,
    keep-alives, guaranteed unsubscribe) is directly unit-testable.
    """
    try:
        while True:
            message = await pubsub.get_message(
                ignore_subscribe_messages=True, timeout=KEEPALIVE_SECONDS
            )
            if message is None:
                yield ": keep-alive\n\n"
                continue
            yield f"data: {message['data']}\n\n"
    finally:
        try:
            await pubsub.unsubscribe(channel)
            await pubsub.aclose()
        except Exception:
            logger.debug("pubsub teardown failed", exc_info=True)


@router.get("/anomalies")
async def anomaly_stream(device_id: str) -> StreamingResponse:
    client = redis.get_client()  # 503 via BackendUnavailableError when down
    channel = f"anomalies:{device_id}"
    pubsub = client.pubsub()
    await pubsub.subscribe(channel)
    return StreamingResponse(
        event_stream(pubsub, channel),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
