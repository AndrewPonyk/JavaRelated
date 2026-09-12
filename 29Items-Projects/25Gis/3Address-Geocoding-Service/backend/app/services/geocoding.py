import time

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.text import collapse_whitespace, normalize_key
from app.schemas.geocode import AddressLookupResponse, AddressRecord
from app.services.address_repository import AddressRepository
from app.services.nominatim import NominatimClient, ProviderResult
from app.services.search import SearchService


class GeocodingService:
    """Forward geocoding (text -> coordinates).

    Calls Nominatim, persists the best match into the local PostGIS store (so it can
    serve future lookups directly), and falls back to local trigram search when the
    upstream provider is disabled or returns nothing.
    """

    def __init__(
        self,
        session: AsyncSession,
        nominatim: NominatimClient,
        settings: Settings,
    ) -> None:
        self.repo = AddressRepository(session)
        self.search = SearchService(session)
        self.nominatim = nominatim
        self.settings = settings

    async def lookup(self, query: str) -> AddressLookupResponse | None:
        started = time.monotonic()
        normalized_query = collapse_whitespace(query)
        if not normalize_key(query):
            return None

        limit = self.settings.geocode_result_limit

        provider_results: list[ProviderResult] = []
        if self.settings.enable_upstream_geocoder:
            provider_results = await self.nominatim.search(normalized_query, limit)

        if provider_results:
            best = provider_results[0]
            best_record = await self.repo.upsert_by_place_id(
                formatted_address=best.formatted_address,
                latitude=best.latitude,
                longitude=best.longitude,
                confidence=best.confidence,
                source="nominatim",
                provider_place_id=best.provider_place_id,
            )
            candidates = [best_record] + [
                _provider_to_record(result) for result in provider_results[1:]
            ]
            await self._log(query, normalized_query, best_record.id, "provider_hit", started)
            return AddressLookupResponse(
                query=query,
                normalized_query=normalized_query,
                best_match=best_record,
                candidates=candidates,
            )

        local = await self.search.suggest_candidates(normalized_query, limit)
        if local:
            await self._log(query, normalized_query, local[0].id, "local_hit", started)
            return AddressLookupResponse(
                query=query,
                normalized_query=normalized_query,
                best_match=local[0],
                candidates=local,
            )

        await self._log(query, normalized_query, None, "miss", started)
        return None

    async def _log(
        self,
        query: str,
        normalized_query: str,
        address_id: int | None,
        status: str,
        started: float,
    ) -> None:
        await self.repo.log_lookup(
            query=query,
            normalized_query=normalized_query,
            address_id=address_id,
            match_status=status,
            latency_ms=int((time.monotonic() - started) * 1000),
        )


def _provider_to_record(result: ProviderResult) -> AddressRecord:
    return AddressRecord(
        formatted_address=result.formatted_address,
        latitude=result.latitude,
        longitude=result.longitude,
        confidence=result.confidence,
        source="nominatim",
    )
