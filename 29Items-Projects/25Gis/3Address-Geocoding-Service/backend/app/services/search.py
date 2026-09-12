from sqlalchemy.ext.asyncio import AsyncSession

from app.schemas.geocode import AddressRecord
from app.services.address_repository import AddressRepository


class SearchService:
    """Fuzzy autocomplete over stored addresses using PostgreSQL ``pg_trgm``.

    This replaces the original Elasticsearch placeholder; the trigram GIN index from
    migration 001 backs the similarity search.
    """

    def __init__(self, session: AsyncSession) -> None:
        self.repo = AddressRepository(session)

    async def suggest_candidates(self, query: str, limit: int = 5) -> list[AddressRecord]:
        return await self.repo.search_trigram(query, limit)
