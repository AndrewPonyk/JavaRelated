"""Instance & series query helpers."""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import NotFoundError
from app.models.instance import Instance
from app.models.series import Series
from app.models.study import Study


class InstanceService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get_by_sop_uid(self, sop_instance_uid: str) -> Instance:
        result = await self._session.execute(
            select(Instance).where(Instance.sop_instance_uid == sop_instance_uid)
        )
        instance = result.scalar_one_or_none()
        if instance is None:
            raise NotFoundError(f"No instance with SOP UID {sop_instance_uid}")
        return instance

    async def get_with_modality(self, sop_instance_uid: str) -> tuple[Instance, str | None]:
        """Resolve an instance along with its series modality (for ML routing)."""
        stmt = (
            select(Instance, Series.modality)
            .join(Series, Instance.series_pk == Series.id)
            .where(Instance.sop_instance_uid == sop_instance_uid)
        )
        row = (await self._session.execute(stmt)).first()
        if row is None:
            raise NotFoundError(f"No instance with SOP UID {sop_instance_uid}")
        return row[0], row[1]

    async def list_for_study(self, study_instance_uid: str) -> list[Instance]:
        """All instances belonging to a study, ordered for stack display."""
        stmt = (
            select(Instance)
            .join(Series, Instance.series_pk == Series.id)
            .join(Study, Series.study_pk == Study.id)
            .where(Study.study_instance_uid == study_instance_uid)
            .order_by(Series.series_number.asc().nullslast(), Instance.instance_number.asc())
        )
        result = await self._session.execute(stmt)
        return list(result.scalars().all())

    async def list_series_for_study(self, study_instance_uid: str) -> list[tuple[Series, int]]:
        """Series of a study with instance counts."""
        instance_count = (
            select(func.count(Instance.id))
            .where(Instance.series_pk == Series.id)
            .correlate(Series)
            .scalar_subquery()
        )
        stmt = (
            select(Series, instance_count)
            .join(Study, Series.study_pk == Study.id)
            .where(Study.study_instance_uid == study_instance_uid)
            .order_by(Series.series_number.asc().nullslast())
        )
        result = await self._session.execute(stmt)
        return [(row[0], int(row[1] or 0)) for row in result.all()]
