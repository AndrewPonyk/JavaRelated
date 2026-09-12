"""PostGIS-backed data access for addresses and lookup auditing.

CRUD goes through the ORM; proximity (``ST_DWithin``/``ST_Distance``) and fuzzy
trigram search (``pg_trgm``) use raw SQL so we can lean on the GIST and GIN indexes
created in migration 001.
"""

from __future__ import annotations

from collections.abc import Mapping

from sqlalchemy import delete, func, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.text import normalize_key
from app.models.address import Address, AddressLookupEvent
from app.schemas.geocode import AddressCreateRequest, AddressRecord


def _record_from_row(row: Mapping) -> AddressRecord:
    return AddressRecord(
        id=row["id"],
        formatted_address=row["formatted_address"],
        latitude=float(row["latitude"]),
        longitude=float(row["longitude"]),
        confidence=float(row["confidence"]),
        source=row["source"],
    )


def _record_from_model(address: Address) -> AddressRecord:
    return AddressRecord(
        id=address.id,
        formatted_address=address.formatted_address,
        latitude=float(address.latitude),
        longitude=float(address.longitude),
        confidence=float(address.confidence),
        source=address.source,
    )


class AddressRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def create(
        self,
        *,
        formatted_address: str,
        latitude: float,
        longitude: float,
        confidence: float,
        source: str,
        provider_place_id: str | None = None,
    ) -> AddressRecord:
        address = Address(
            formatted_address=formatted_address,
            normalized_address=normalize_key(formatted_address),
            source=source,
            provider_place_id=provider_place_id,
            confidence=confidence,
            latitude=latitude,
            longitude=longitude,
        )
        self.session.add(address)
        await self.session.commit()
        await self.session.refresh(address)
        return _record_from_model(address)

    async def upsert_by_place_id(
        self,
        *,
        formatted_address: str,
        latitude: float,
        longitude: float,
        confidence: float,
        source: str,
        provider_place_id: str | None,
    ) -> AddressRecord:
        """Insert a provider result, or update the existing row with that place id.

        Keeps the local store free of duplicate provider rows so it can grow into a
        useful cache for search and reverse lookups.
        """
        if provider_place_id is not None:
            existing = await self.session.scalar(
                select(Address).where(Address.provider_place_id == provider_place_id)
            )
            if existing is not None:
                existing.formatted_address = formatted_address
                existing.normalized_address = normalize_key(formatted_address)
                existing.latitude = latitude
                existing.longitude = longitude
                existing.confidence = confidence
                existing.source = source
                existing.updated_at = func.now()
                await self.session.commit()
                await self.session.refresh(existing)
                return _record_from_model(existing)

        return await self.create(
            formatted_address=formatted_address,
            latitude=latitude,
            longitude=longitude,
            confidence=confidence,
            source=source,
            provider_place_id=provider_place_id,
        )

    async def list(self, *, limit: int, offset: int) -> tuple[list[AddressRecord], int]:
        total = await self.session.scalar(select(func.count()).select_from(Address)) or 0
        rows = await self.session.scalars(
            select(Address)
            .order_by(Address.created_at.desc(), Address.id.desc())
            .limit(limit)
            .offset(offset)
        )
        return [_record_from_model(row) for row in rows], int(total)

    async def get(self, address_id: int) -> AddressRecord | None:
        address = await self.session.get(Address, address_id)
        return _record_from_model(address) if address is not None else None

    async def update(self, address_id: int, payload: AddressCreateRequest) -> AddressRecord | None:
        address = await self.session.get(Address, address_id)
        if address is None:
            return None
        address.formatted_address = payload.formatted_address
        address.normalized_address = normalize_key(payload.formatted_address)
        address.latitude = payload.latitude
        address.longitude = payload.longitude
        address.confidence = payload.confidence
        address.source = payload.source
        address.updated_at = func.now()
        await self.session.commit()
        await self.session.refresh(address)
        return _record_from_model(address)

    async def delete(self, address_id: int) -> bool:
        result = await self.session.execute(delete(Address).where(Address.id == address_id))
        await self.session.commit()
        return (result.rowcount or 0) > 0

    async def search_trigram(self, query: str, limit: int) -> list[AddressRecord]:
        normalized = normalize_key(query)
        if not normalized:
            return []
        result = await self.session.execute(
            text(
                """
                SELECT id, formatted_address, latitude, longitude, confidence, source,
                       similarity(normalized_address, :q) AS score
                FROM addresses
                WHERE normalized_address % :q
                ORDER BY score DESC, confidence DESC
                LIMIT :limit
                """
            ),
            {"q": normalized, "limit": limit},
        )
        return [_record_from_row(row._mapping) for row in result]

    async def nearest(
        self, latitude: float, longitude: float, radius_meters: float, limit: int
    ) -> list[AddressRecord]:
        result = await self.session.execute(
            text(
                """
                SELECT id, formatted_address, latitude, longitude, confidence, source,
                       ST_Distance(
                           geom,
                           ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                       ) AS distance_m
                FROM addresses
                WHERE ST_DWithin(
                    geom,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                    :radius_m
                )
                ORDER BY distance_m ASC
                LIMIT :limit
                """
            ),
            {"lat": latitude, "lon": longitude, "radius_m": radius_meters, "limit": limit},
        )
        return [_record_from_row(row._mapping) for row in result]

    async def log_lookup(
        self,
        *,
        query: str,
        normalized_query: str,
        address_id: int | None,
        match_status: str,
        latency_ms: int | None,
    ) -> None:
        self.session.add(
            AddressLookupEvent(
                query=query,
                normalized_query=normalized_query,
                address_id=address_id,
                match_status=match_status,
                latency_ms=latency_ms,
            )
        )
        await self.session.commit()
