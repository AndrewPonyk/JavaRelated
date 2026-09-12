"""Fail-open Redis result cache.

Used to memoize deterministic, expensive computations (e.g. an efficient
frontier for a fixed covariance window). Every operation degrades gracefully:
if Redis is unavailable or disabled, reads return ``None`` and writes no-op, so
the caller simply recomputes. The cache never raises into request handling.
"""

from __future__ import annotations

import hashlib
import json
import logging
from typing import Any

from app.core.config import get_settings

logger = logging.getLogger("app.cache")

_settings = get_settings()
_client: Any = None
_unavailable = False


def _get_client() -> Any:
    global _client, _unavailable
    if not _settings.cache_enabled or _unavailable:
        return None
    if _client is None:
        try:
            import redis

            _client = redis.Redis.from_url(
                _settings.redis_url, socket_connect_timeout=0.3, socket_timeout=0.3
            )
            _client.ping()
        except Exception as exc:  # noqa: BLE001 - fail open
            logger.warning("Cache unavailable, continuing without it: %s", exc)
            _unavailable = True
            _client = None
    return _client


def make_key(prefix: str, payload: dict) -> str:
    raw = json.dumps(payload, sort_keys=True, default=str)
    digest = hashlib.sha256(raw.encode()).hexdigest()[:16]
    return f"{prefix}:{digest}"


def get_json(key: str) -> Any | None:
    client = _get_client()
    if client is None:
        return None
    try:
        value = client.get(key)
        return json.loads(value) if value else None
    except Exception as exc:  # noqa: BLE001 - fail open
        logger.warning("Cache get failed: %s", exc)
        return None


def set_json(key: str, value: Any, ttl: int | None = None) -> None:
    client = _get_client()
    if client is None:
        return
    try:
        client.set(key, json.dumps(value, default=str), ex=ttl or _settings.cache_ttl_seconds)
    except Exception as exc:  # noqa: BLE001 - fail open
        logger.warning("Cache set failed: %s", exc)


def ping() -> bool:
    """Best-effort connectivity probe for the readiness endpoint."""
    client = _get_client()
    if client is None:
        return False
    try:
        return bool(client.ping())
    except Exception:  # noqa: BLE001
        return False
