"""StudyService query tests: filters, paging, counts, 404."""

from __future__ import annotations

import pytest
from app.core.exceptions import StudyNotFoundError
from app.schemas.study import StudyQuery
from app.services.ingestion_service import IngestionService
from app.services.study_service import StudyService

from tests.conftest import build_dicom


async def _seed(db, storage):
    svc = IngestionService(db, storage)
    await svc.ingest(
        build_dicom(
            "DX",
            PatientID="P1",
            StudyInstanceUID="S1",
            SeriesInstanceUID="SE1",
            StudyDate="20260101",
        )
    )
    await svc.ingest(
        build_dicom(
            "CT",
            PatientID="P2",
            StudyInstanceUID="S2",
            SeriesInstanceUID="SE2",
            StudyDate="20260202",
        )
    )
    await db.commit()


async def test_search_returns_all(db, storage):
    await _seed(db, storage)
    rows, total = await StudyService(db).search(StudyQuery())
    assert total == 2
    assert len(rows) == 2
    # series_count is populated
    assert all(count >= 1 for _, count in rows)


async def test_search_filter_by_patient(db, storage):
    await _seed(db, storage)
    rows, total = await StudyService(db).search(StudyQuery(patient_id="P1"))
    assert total == 1
    assert rows[0][0].study_instance_uid == "S1"


async def test_search_filter_by_modality(db, storage):
    await _seed(db, storage)
    rows, total = await StudyService(db).search(StudyQuery(modality="CT"))
    assert total == 1
    assert rows[0][0].study_instance_uid == "S2"


async def test_search_date_range(db, storage):
    await _seed(db, storage)
    _, total = await StudyService(db).search(
        StudyQuery(study_date_from="20260201", study_date_to="20260301")
    )
    assert total == 1


async def test_search_paging(db, storage):
    await _seed(db, storage)
    rows, total = await StudyService(db).search(StudyQuery(limit=1, offset=0))
    assert total == 2
    assert len(rows) == 1


async def test_get_by_uid_found(db, storage):
    await _seed(db, storage)
    study, count = await StudyService(db).get_by_uid("S1")
    assert study.study_instance_uid == "S1"
    assert count == 1


async def test_get_by_uid_missing_raises(db):
    with pytest.raises(StudyNotFoundError):
        await StudyService(db).get_by_uid("does-not-exist")


async def test_qido_rows_include_patient(db, storage):
    await _seed(db, storage)
    rows = await StudyService(db).qido_rows(StudyQuery(patient_id="P1"))
    assert len(rows) == 1
    _, _, patient_id, _ = rows[0]
    assert patient_id == "P1"
