"""Redis client lifecycle (async client, used for live aggregates + pub/sub).

Redis holds ONLY rebuildable state (cache semantics): every key gets a TTL,
and losing it never loses device data (docs/ARCHITECTURE.md §2.2).
"""

from __future__ import annotations

import logging
from typing import Any

from app.core.config import settings
from app.db import BackendUnavailableError

logger = logging.getLogger(__name__)

_client: Any = None


async def connect() -> None:
    global _client
    if _client is not None:
        return
    import redis.asyncio as aioredis

    client = aioredis.from_url(
        settings.redis_url,
        decode_responses=True,
        socket_connect_timeout=5,
    )
    await client.ping()
    _client = client


def get_client() -> Any:
    if _client is None:
        raise BackendUnavailableError("Redis is not available")
    return _client


async def close() -> None:
    global _client
    if _client is not None:
        await _client.aclose()
    _client = None
