"""Async Nominatim (OpenStreetMap) geocoding client.

Wraps the public Nominatim ``/search`` and ``/reverse`` endpoints. Results are
cached in-process with a short TTL: Nominatim's usage policy asks for at most one
request per second and caching of results, and it also keeps repeated lookups fast.
Network/HTTP failures are swallowed and surfaced as empty results so callers can
fall back to the local PostGIS store.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass

import httpx

from app.core.config import Settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ProviderResult:
    formatted_address: str
    latitude: float
    longitude: float
    confidence: float
    provider_place_id: str | None


def _clamp_confidence(value: object, default: float) -> float:
    try:
        confidence = float(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return default
    return max(0.0, min(1.0, confidence))


class NominatimClient:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.nominatim_base_url
        self._user_agent = settings.nominatim_user_agent
        self._timeout = settings.nominatim_timeout_seconds
        self._cache_ttl = settings.nominatim_cache_ttl_seconds
        self._cache: dict[tuple[str, str], tuple[float, list[ProviderResult]]] = {}

    def _cache_get(self, key: tuple[str, str]) -> list[ProviderResult] | None:
        entry = self._cache.get(key)
        if entry is None:
            return None
        expires_at, value = entry
        if expires_at < time.monotonic():
            self._cache.pop(key, None)
            return None
        return value

    def _cache_set(self, key: tuple[str, str], value: list[ProviderResult]) -> None:
        self._cache[key] = (time.monotonic() + self._cache_ttl, value)

    async def _get(self, path: str, params: dict[str, str | int | float]) -> object | None:
        headers = {"User-Agent": self._user_agent}
        try:
            async with httpx.AsyncClient(
                base_url=self._base_url, headers=headers, timeout=self._timeout
            ) as client:
                response = await client.get(path, params=params)
                response.raise_for_status()
                return response.json()
        except (httpx.HTTPError, ValueError) as exc:
            logger.warning("nominatim request failed", extra={"path": path, "error": str(exc)})
            return None

    async def search(self, query: str, limit: int) -> list[ProviderResult]:
        key = ("search", f"{query}|{limit}")
        cached = self._cache_get(key)
        if cached is not None:
            return cached

        payload = await self._get(
            "/search",
            {"q": query, "format": "jsonv2", "addressdetails": 0, "limit": limit},
        )
        results: list[ProviderResult] = []
        if isinstance(payload, list):
            for item in payload:
                parsed = self._parse_item(item, default_confidence=0.5)
                if parsed is not None:
                    results.append(parsed)

        self._cache_set(key, results)
        return results

    async def reverse(self, latitude: float, longitude: float) -> ProviderResult | None:
        key = ("reverse", f"{latitude:.6f}|{longitude:.6f}")
        cached = self._cache_get(key)
        if cached is not None:
            return cached[0] if cached else None

        payload = await self._get(
            "/reverse",
            {"lat": latitude, "lon": longitude, "format": "jsonv2", "addressdetails": 0},
        )
        result: ProviderResult | None = None
        if isinstance(payload, dict) and "error" not in payload:
            result = self._parse_item(payload, default_confidence=0.6)

        self._cache_set(key, [result] if result is not None else [])
        return result

    @staticmethod
    def _parse_item(item: object, *, default_confidence: float) -> ProviderResult | None:
        if not isinstance(item, dict):
            return None
        display_name = item.get("display_name")
        lat = item.get("lat")
        lon = item.get("lon")
        if not display_name or lat is None or lon is None:
            return None
        try:
            latitude = float(lat)
            longitude = float(lon)
        except (TypeError, ValueError):
            return None

        place_id = item.get("place_id")
        return ProviderResult(
            formatted_address=str(display_name),
            latitude=latitude,
            longitude=longitude,
            confidence=_clamp_confidence(item.get("importance"), default_confidence),
            provider_place_id=str(place_id) if place_id is not None else None,
        )
