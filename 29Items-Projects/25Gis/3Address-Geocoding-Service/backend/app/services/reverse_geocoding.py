import time

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.schemas.geocode import AddressRecord
from app.services.address_repository import AddressRepository
from app.services.nominatim import NominatimClient


class ReverseGeocodingService:
    """Find the nearest known address to a coordinate.

    Answers from the local PostGIS store first (``ST_DWithin``/``ST_Distance``); if
    nothing is within the configured radius it optionally falls back to Nominatim
    reverse geocoding and caches that result back into the store.
    """

    def __init__(
        self,
        session: AsyncSession,
        nominatim: NominatimClient,
        settings: Settings,
    ) -> None:
        self.repo = AddressRepository(session)
        self.nominatim = nominatim
        self.settings = settings

    async def find_nearest(self, latitude: float, longitude: float) -> AddressRecord | None:
        started = time.monotonic()
        query = f"{latitude:.6f},{longitude:.6f}"

        local = await self.repo.nearest(
            latitude,
            longitude,
            radius_meters=self.settings.reverse_search_radius_meters,
            limit=1,
        )
        if local:
            await self._log(query, local[0].id, "local_hit", started)
            return local[0]

        if self.settings.enable_upstream_geocoder:
            provider = await self.nominatim.reverse(latitude, longitude)
            if provider is not None:
                record = await self.repo.upsert_by_place_id(
                    formatted_address=provider.formatted_address,
                    latitude=provider.latitude,
                    longitude=provider.longitude,
                    confidence=provider.confidence,
                    source="nominatim",
                    provider_place_id=provider.provider_place_id,
                )
                await self._log(query, record.id, "provider_hit", started)
                return record

        await self._log(query, None, "miss", started)
        return None

    async def _log(self, query: str, address_id: int | None, status: str, started: float) -> None:
        await self.repo.log_lookup(
            query=query,
            normalized_query=query,
            address_id=address_id,
            match_status=status,
            latency_ms=int((time.monotonic() - started) * 1000),
        )
