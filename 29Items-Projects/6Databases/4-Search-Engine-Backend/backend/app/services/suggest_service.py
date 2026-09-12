"""Autocomplete use-case: Redis cache-aside in front of the ES completion suggester.

Redis is strictly an optimization — any cache failure is logged and treated as a miss.
Keys are versioned (`suggest:v1:`) so suggestion-logic changes just bump the namespace
instead of trying to invalidate selectively (TECH-NOTES §3.6 #7).
"""

import json
import random
from typing import Any

import structlog
from elasticsearch import ApiError, TransportError

from app.core.config import Settings
from app.core.exceptions import SearchBackendUnavailableError

logger = structlog.get_logger(__name__)

CACHE_KEY_PREFIX = "suggest:v1:"
SUGGEST_FIELD = "suggest"
SUGGESTER_NAME = "product_suggest"


class SuggestService:
    def __init__(self, es: Any, redis: Any, settings: Settings) -> None:
        self._es = es
        self._redis = redis
        self._settings = settings

    async def suggest(self, prefix: str, limit: int = 8) -> list[str]:
        normalized = " ".join(prefix.lower().split())
        if len(normalized) < self._settings.suggest_min_prefix_len:
            return []

        cache_key = f"{CACHE_KEY_PREFIX}{normalized}:{limit}"

        cached = await self._cache_get(cache_key)
        if cached is not None:
            return cached

        suggestions = await self._query_es(normalized, limit)
        await self._cache_set(cache_key, suggestions)
        return suggestions

    async def _query_es(self, prefix: str, limit: int) -> list[str]:
        try:
            response = await self._es.search(
                index=self._settings.es_products_alias,
                suggest={
                    SUGGESTER_NAME: {
                        "prefix": prefix,
                        "completion": {
                            "field": SUGGEST_FIELD,
                            "size": limit,
                            "skip_duplicates": True,
                            "fuzzy": {"fuzziness": "AUTO"},
                        },
                    }
                },
                _source=False,
            )
        except (ApiError, TransportError) as exc:
            logger.error("es_suggest_failed", error=str(exc))
            raise SearchBackendUnavailableError() from exc

        options = response["suggest"][SUGGESTER_NAME][0]["options"]
        return [option["text"] for option in options]

    async def _cache_get(self, key: str) -> list[str] | None:
        try:
            raw = await self._redis.get(key)
        except Exception as exc:  # noqa: BLE001 — cache failure == cache miss, by design
            logger.warning("suggest_cache_get_failed", error=str(exc))
            return None
        return json.loads(raw) if raw else None

    async def _cache_set(self, key: str, values: list[str]) -> None:
        ttl = self._settings.suggest_cache_ttl_s + random.randint(
            0, self._settings.suggest_cache_jitter_s
        )
        try:
            await self._redis.setex(key, ttl, json.dumps(values))
        except Exception as exc:  # noqa: BLE001
            logger.warning("suggest_cache_set_failed", error=str(exc))
