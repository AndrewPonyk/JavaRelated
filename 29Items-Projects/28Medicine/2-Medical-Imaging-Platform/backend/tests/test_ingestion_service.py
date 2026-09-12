"""IngestionService tests: hierarchy upsert, idempotency, de-identification."""

from __future__ import annotations

from io import BytesIO

import pydicom
from app.core.config import settings
from app.models.instance import Instance
from app.models.patient import Patient
from app.models.series import Series
from app.models.study import Study
from app.services.ingestion_service import IngestionService
from sqlalchemy import func, select

from tests.conftest import build_dicom


async def _count(db, model) -> int:
    return await db.scalar(select(func.count(model.id)))


async def test_ingest_creates_full_hierarchy(db, storage):
    raw = build_dicom("DX")
    result = await IngestionService(db, storage).ingest(raw)
    await db.commit()

    assert result.created is True
    assert await _count(db, Patient) == 1
    assert await _count(db, Study) == 1
    assert await _count(db, Series) == 1
    assert await _count(db, Instance) == 1
    # object actually archived
    assert storage.object_exists(settings.s3_bucket_dicom, result.object_key)


async def test_ingest_is_idempotent(db, storage):
    raw = build_dicom("DX")
    svc = IngestionService(db, storage)
    first = await svc.ingest(raw)
    second = await svc.ingest(raw)
    await db.commit()

    assert first.created is True
    assert second.created is False
    assert await _count(db, Instance) == 1  # no duplicate


async def test_multiple_series_same_study(db, storage):
    study_uid = "1.2.3.999"
    a = build_dicom("DX", StudyInstanceUID=study_uid, SeriesInstanceUID="1.1")
    b = build_dicom("CT", StudyInstanceUID=study_uid, SeriesInstanceUID="1.2")
    svc = IngestionService(db, storage)
    await svc.ingest(a)
    await svc.ingest(b)
    await db.commit()

    assert await _count(db, Study) == 1
    assert await _count(db, Series) == 2
    study = await db.scalar(select(Study).where(Study.study_instance_uid == study_uid))
    assert set((study.modalities or "").split("\\")) == {"CT", "DX"}


async def test_deidentify_on_ingest(db, storage, monkeypatch):
    monkeypatch.setattr(settings, "deidentify_on_ingest", True)
    raw = build_dicom("DX", PatientName="Secret^Patient")
    result = await IngestionService(db, storage).ingest(raw)
    await db.commit()

    stored = storage.get_object(settings.s3_bucket_dicom, result.object_key)
    ds = pydicom.dcmread(BytesIO(stored))
    assert str(ds.PatientName) == ""
    assert ds.PatientIdentityRemoved == "YES"
