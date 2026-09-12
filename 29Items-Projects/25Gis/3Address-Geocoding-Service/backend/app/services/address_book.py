from sqlalchemy.ext.asyncio import AsyncSession

from app.schemas.geocode import AddressCreateRequest, AddressRecord
from app.services.address_repository import AddressRepository


class AddressBookService:
    """CRUD over the persistent PostGIS ``addresses`` table."""

    def __init__(self, session: AsyncSession) -> None:
        self.repo = AddressRepository(session)

    async def create(self, payload: AddressCreateRequest) -> AddressRecord:
        return await self.repo.create(
            formatted_address=payload.formatted_address,
            latitude=payload.latitude,
            longitude=payload.longitude,
            confidence=payload.confidence,
            source=payload.source,
        )

    async def list(self, *, limit: int, offset: int) -> tuple[list[AddressRecord], int]:
        return await self.repo.list(limit=limit, offset=offset)

    async def get(self, address_id: int) -> AddressRecord | None:
        return await self.repo.get(address_id)

    async def update(self, address_id: int, payload: AddressCreateRequest) -> AddressRecord | None:
        return await self.repo.update(address_id, payload)

    async def delete(self, address_id: int) -> bool:
        return await self.repo.delete(address_id)
