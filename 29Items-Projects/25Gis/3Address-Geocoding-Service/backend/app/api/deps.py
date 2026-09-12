from functools import lru_cache

from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.db.session import get_session
from app.services.address_book import AddressBookService
from app.services.geocoding import GeocodingService
from app.services.nominatim import NominatimClient
from app.services.reverse_geocoding import ReverseGeocodingService
from app.services.search import SearchService


@lru_cache
def get_nominatim_client() -> NominatimClient:
    """Process-wide Nominatim client so its in-memory response cache is shared."""
    return NominatimClient(get_settings())


def get_address_book_service(
    session: AsyncSession = Depends(get_session),
) -> AddressBookService:
    return AddressBookService(session)


def get_search_service(
    session: AsyncSession = Depends(get_session),
) -> SearchService:
    return SearchService(session)


def get_geocoding_service(
    session: AsyncSession = Depends(get_session),
    nominatim: NominatimClient = Depends(get_nominatim_client),
    settings: Settings = Depends(get_settings),
) -> GeocodingService:
    return GeocodingService(session, nominatim, settings)


def get_reverse_geocoding_service(
    session: AsyncSession = Depends(get_session),
    nominatim: NominatimClient = Depends(get_nominatim_client),
    settings: Settings = Depends(get_settings),
) -> ReverseGeocodingService:
    return ReverseGeocodingService(session, nominatim, settings)
