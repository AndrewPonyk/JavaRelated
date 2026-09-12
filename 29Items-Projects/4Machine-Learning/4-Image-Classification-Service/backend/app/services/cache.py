"""Redis read-through cache for predictions.

The cache is an OPTIMIZATION, never a hard dependency. If Redis is unreachable, every
method logs a warning and behaves as a cache miss so the request still succeeds via
the model.

Keys are namespaced by model version so a new model deploy transparently invalidates
stale predictions without an explicit flush.
"""

from __future__ import annotations

import json

from app.core.logging import get_logger
from app.models.schemas import LabelPrediction

logger = get_logger(__name__)


class CacheService:
    """Thin wrapper over a redis client with graceful degradation."""

    def __init__(self, client, ttl_seconds: int, model_version: str) -> None:
        # ``client`` is a redis.Redis (or fakeredis) instance, or None to disable.
        self._client = client
        self._ttl = ttl_seconds
        self._model_version = model_version

    @classmethod
    def from_url(cls, url: str | None, ttl_seconds: int, model_version: str) -> CacheService:
        client = None
        if url:
            try:
                import redis  # local import keeps it optional

                client = redis.Redis.from_url(url, socket_timeout=0.25)
                client.ping()
            except Exception as exc:  # noqa: BLE001
                logger.warning("cache_init_failed", extra={"extra": {"error": str(exc)}})
                client = None
        return cls(client=client, ttl_seconds=ttl_seconds, model_version=model_version)

    def _key(self, image_hash: str) -> str:
        return f"clf:{self._model_version}:{image_hash}"

    def get(self, image_hash: str) -> list[LabelPrediction] | None:
        if self._client is None:
            return None
        try:
            raw = self._client.get(self._key(image_hash))
            if raw is None:
                return None
            data = json.loads(raw)
            return [LabelPrediction(**d) for d in data]
        except Exception as exc:  # noqa: BLE001
            logger.warning("cache_get_failed", extra={"extra": {"error": str(exc)}})
            return None

    def set(self, image_hash: str, predictions: list[LabelPrediction]) -> None:
        if self._client is None:
            return
        try:
            payload = json.dumps([p.model_dump() for p in predictions])
            self._client.setex(self._key(image_hash), self._ttl, payload)
        except Exception as exc:  # noqa: BLE001
            logger.warning("cache_set_failed", extra={"extra": {"error": str(exc)}})
