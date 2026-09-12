"""Device registry service: repository + Redis permission cache + key checks.

The ingest hot path asks "may this device write?" thousands of times a minute;
the answer is cached in Redis ("1"/"0", short TTL) so Cassandra is consulted at
most once per device per DEVICE_CACHE_TTL_SECONDS. Mutations invalidate the
cache entry. If Redis is down the check falls through to Cassandra directly.
"""

from __future__ import annotations

import contextlib
import logging
import secrets

from app.core.config import settings
from app.core.security import (
    compose_device_key,
    generate_device_secret,
    hash_api_key,
    verify_api_key,
)
from app.db import BackendUnavailableError, redis
from app.repositories import devices as devices_repo
from app.schemas.device import Device, DeviceCreate, DeviceWithKey

logger = logging.getLogger(__name__)


def _cache_key(device_id: str) -> str:
    return f"device:{device_id}:ingest"


async def ingest_allowed(device_id: str) -> bool:
    """True iff the device exists and is enabled (cached)."""
    client = None
    try:
        client = redis.get_client()
        cached = await client.get(_cache_key(device_id))
        if cached is not None:
            return cached == "1"
    except BackendUnavailableError:
        client = None
    except Exception:  # cache read trouble → treat as miss, use the registry
        logger.warning("device cache read failed for %s", device_id, exc_info=True)

    device = await devices_repo.get(device_id)
    allowed = device is not None and device.enabled
    if client is not None:
        with contextlib.suppress(Exception):
            await client.set(
                _cache_key(device_id),
                "1" if allowed else "0",
                ex=settings.device_cache_ttl_seconds,
            )
    return allowed


async def invalidate_cache(device_id: str) -> None:
    with contextlib.suppress(Exception):
        await redis.get_client().delete(_cache_key(device_id))


async def verify_device_secret(device_id: str, secret: str) -> bool:
    stored_hash = await devices_repo.get_api_key_hash(device_id)
    return stored_hash is not None and verify_api_key(secret, stored_hash)


async def register(payload: DeviceCreate) -> DeviceWithKey:
    """Create a device and mint its credential (returned exactly once)."""
    device = Device(
        device_id=f"dev-{secrets.token_hex(6)}",
        name=payload.name,
        site=payload.site,
        device_type=payload.device_type,
        enabled=True,
    )
    secret = generate_device_secret()
    await devices_repo.insert(device, api_key_hash=hash_api_key(secret))
    await invalidate_cache(device.device_id)
    return DeviceWithKey(
        **device.model_dump(),
        api_key=compose_device_key(device.device_id, secret),
    )


async def get(device_id: str) -> Device | None:
    return await devices_repo.get(device_id)


async def list_all() -> list[Device]:
    return await devices_repo.list_all()


async def set_enabled(device_id: str, enabled: bool) -> None:
    await devices_repo.set_enabled(device_id, enabled)
    await invalidate_cache(device_id)


async def delete(device_id: str) -> None:
    await devices_repo.delete(device_id)
    await invalidate_cache(device_id)
