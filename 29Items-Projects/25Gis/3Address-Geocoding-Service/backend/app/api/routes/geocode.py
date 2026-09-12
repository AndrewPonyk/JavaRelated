from fastapi import APIRouter, Depends, HTTPException, Query, status

from app.api.deps import (
    get_geocoding_service,
    get_reverse_geocoding_service,
    get_search_service,
)
from app.schemas.geocode import (
    AddressLookupRequest,
    AddressLookupResponse,
    AddressRecord,
    ReverseGeocodeRequest,
)
from app.services.geocoding import GeocodingService
from app.services.reverse_geocoding import ReverseGeocodingService
from app.services.search import SearchService

router = APIRouter()


@router.post("/lookup", response_model=AddressLookupResponse)
async def lookup_address(
    payload: AddressLookupRequest,
    service: GeocodingService = Depends(get_geocoding_service),
) -> AddressLookupResponse:
    result = await service.lookup(payload.query)
    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No address match found",
        )
    return result


@router.get("/search", response_model=list[AddressRecord])
async def search_addresses(
    q: str = Query(min_length=1, max_length=500),
    limit: int = Query(default=5, ge=1, le=25),
    service: SearchService = Depends(get_search_service),
) -> list[AddressRecord]:
    """Fuzzy autocomplete over stored addresses (PostgreSQL trigram similarity)."""
    return await service.suggest_candidates(q, limit)


@router.post("/reverse", response_model=AddressRecord)
async def reverse_geocode(
    payload: ReverseGeocodeRequest,
    service: ReverseGeocodingService = Depends(get_reverse_geocoding_service),
) -> AddressRecord:
    result = await service.find_nearest(payload.latitude, payload.longitude)
    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No nearby address found",
        )
    return result
