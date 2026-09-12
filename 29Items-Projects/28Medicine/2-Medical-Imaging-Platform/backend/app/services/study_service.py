"""Study query service — worklist search and retrieval with derived counts."""

from __future__ import annotations

from typing import Any

from sqlalchemy import ScalarSelect, Select, exists, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import StudyNotFoundError
from app.models.patient import Patient
from app.models.series import Series
from app.models.study import Study
from app.schemas.study import StudyQuery


class StudyService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    @staticmethod
    def _series_count_subq() -> ScalarSelect[int]:
        return (
            select(func.count(Series.id))
            .where(Series.study_pk == Study.id)
            .correlate(Study)
            .scalar_subquery()
        )

    def _apply_filters(self, stmt: Select[Any], q: StudyQuery) -> Select[Any]:
        if q.patient_id:
            # EXISTS (not JOIN) so callers can join Patient for output freely.
            stmt = stmt.where(
                exists()
                .where(Patient.id == Study.patient_pk)
                .where(Patient.patient_id == q.patient_id)
            )
        if q.accession_number:
            stmt = stmt.where(Study.accession_number == q.accession_number)
        if q.modality:
            stmt = stmt.where(
                exists().where(
                    (Series.study_pk == Study.id) & (Series.modality == q.modality.upper())
                )
            )
        if q.study_date_from:
            stmt = stmt.where(Study.study_date >= q.study_date_from)
        if q.study_date_to:
            stmt = stmt.where(Study.study_date <= q.study_date_to)
        return stmt

    async def search(self, q: StudyQuery) -> tuple[list[tuple[Study, int]], int]:
        """Return ((study, series_count) rows for the page, total match count)."""
        series_count = self._series_count_subq().label("series_count")

        base = self._apply_filters(select(Study), q)
        total = await self._session.scalar(select(func.count()).select_from(base.subquery()))

        rows_stmt = (
            self._apply_filters(select(Study, series_count), q)
            .order_by(Study.study_date.desc().nullslast(), Study.created_at.desc())
            .limit(q.limit)
            .offset(q.offset)
        )
        result = await self._session.execute(rows_stmt)
        rows = [(row[0], int(row[1] or 0)) for row in result.all()]
        return rows, int(total or 0)

    async def qido_rows(self, q: StudyQuery) -> list[tuple[Study, int, str | None, str | None]]:
        """Like search() but also returns patient id/name for DICOM JSON output.

        Patient is joined here (for output), so the patient filter is applied on
        the join directly rather than via _apply_filters' EXISTS subquery.
        """
        series_count = self._series_count_subq().label("series_count")
        stmt = select(Study, series_count, Patient.patient_id, Patient.patient_name).join(
            Patient, Study.patient_pk == Patient.id
        )

        if q.patient_id:
            stmt = stmt.where(Patient.patient_id == q.patient_id)
        if q.accession_number:
            stmt = stmt.where(Study.accession_number == q.accession_number)
        if q.modality:
            stmt = stmt.where(
                exists().where(
                    (Series.study_pk == Study.id) & (Series.modality == q.modality.upper())
                )
            )
        if q.study_date_from:
            stmt = stmt.where(Study.study_date >= q.study_date_from)
        if q.study_date_to:
            stmt = stmt.where(Study.study_date <= q.study_date_to)

        stmt = stmt.order_by(Study.study_date.desc().nullslast()).limit(q.limit).offset(q.offset)
        result = await self._session.execute(stmt)
        return [(r[0], int(r[1] or 0), r[2], r[3]) for r in result.all()]

    async def get_by_uid(self, study_instance_uid: str) -> tuple[Study, int]:
        """Fetch a single study + its series count, or raise 404."""
        series_count = self._series_count_subq().label("series_count")
        stmt = select(Study, series_count).where(Study.study_instance_uid == study_instance_uid)
        result = await self._session.execute(stmt)
        row = result.first()
        if row is None:
            raise StudyNotFoundError(f"No study with UID {study_instance_uid}")
        return row[0], int(row[1] or 0)
