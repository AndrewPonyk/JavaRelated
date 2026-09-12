import json
import logging
from typing import Any

import redis

from app.core.config import settings

logger = logging.getLogger(__name__)


class ActiveVehicleCache:
    def __init__(self) -> None:
        self._client: redis.Redis | None = None
        try:
            self._client = redis.from_url(settings.redis_url, decode_responses=True)
            self._client.ping()
        except redis.RedisError as exc:
            logger.info("redis unavailable; continuing without cache", extra={"error": str(exc)})
            self._client = None

    def set_vehicle(self, vehicle_id: str, payload: dict[str, Any]) -> None:
        if self._client is None:
            return
        self._client.setex(
            f"vehicle:{vehicle_id}:active",
            settings.active_vehicle_cache_ttl_seconds,
            json.dumps(payload, default=str),
        )

    def get_vehicle(self, vehicle_id: str) -> dict[str, Any] | None:
        if self._client is None:
            return None
        cached = self._client.get(f"vehicle:{vehicle_id}:active")
        return json.loads(cached) if cached else None
