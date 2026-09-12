"""Ingestion orchestration: raw DICOM bytes → archive + metadata rows.

Pipeline: parse → (optional de-id) → archive object → idempotent upsert of
Patient/Study/Series/Instance → audit → trigger ML for chest X-rays.

Idempotent on SOPInstanceUID (select-then-write within the txn) so SQS
at-least-once redelivery and re-uploads are safe. Called from the API (inline
dev mode) or the ingestion worker (queue mode) — same code path either way.
"""

from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO

import pydicom
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.logging import get_logger
from app.models.instance import Instance
from app.models.patient import Patient
from app.models.series import Series
from app.models.study import Study
from app.services.audit_service import AuditService
from app.services.deidentify_service import deidentify
from app.services.dicom_service import DicomMetadata, build_object_key, parse_metadata
from app.services.ml_service import MlService
from app.services.storage_service import StorageService

log = get_logger(__name__)


@dataclass(slots=True)
class IngestResult:
    sop_instance_uid: str
    series_instance_uid: str
    study_instance_uid: str
    sop_class_uid: str | None
    object_key: str
    created: bool  # False if the instance already existed (idempotent re-ingest)
    queued_for_ml: bool
    ml_ran: bool


class IngestionService:
    def __init__(self, session: AsyncSession, storage: StorageService) -> None:
        self._session = session
        self._storage = storage
        self._audit = AuditService(session)

    async def ingest(self, raw: bytes, *, actor_id: str = "system") -> IngestResult:
        if settings.deidentify_on_ingest:
            raw = self._deidentify_bytes(raw)

        meta = parse_metadata(BytesIO(raw))
        key = build_object_key(meta)

        self._storage.put_object(
            settings.s3_bucket_dicom, key, raw, content_type="application/dicom"
        )

        instance, created = await self._upsert_hierarchy(meta, key, size=len(raw))

        await self._audit.record(
            actor_id=actor_id,
            action="create" if created else "update",
            resource_type="instance",
            resource_id=meta.sop_instance_uid,
        )

        queued = ml_ran = False
        if created and meta.is_chest_xray and settings.ml_enabled:
            ml = MlService(self._session)
            if settings.ml_inline:
                ml_ran = await ml.infer_and_store(instance) is not None
            else:
                ml.enqueue(instance)
                queued = True

        log.info(
            "ingest.complete",
            object_key=key,
            created=created,
            queued_for_ml=queued,
            ml_ran=ml_ran,
        )
        return IngestResult(
            sop_instance_uid=meta.sop_instance_uid,
            series_instance_uid=meta.series_instance_uid,
            study_instance_uid=meta.study_instance_uid,
            sop_class_uid=meta.sop_class_uid,
            object_key=key,
            created=created,
            queued_for_ml=queued,
            ml_ran=ml_ran,
        )

    # ── helpers ──────────────────────────────────────────────
    @staticmethod
    def _deidentify_bytes(raw: bytes) -> bytes:
        deid = deidentify(pydicom.dcmread(BytesIO(raw)))
        out = BytesIO()
        deid.save_as(out, write_like_original=False)
        return out.getvalue()

    async def _upsert_hierarchy(
        self, meta: DicomMetadata, object_key: str, size: int
    ) -> tuple[Instance, bool]:
        patient = await self._get_or_create_patient(meta)
        study = await self._get_or_create_study(meta, patient)
        series = await self._get_or_create_series(meta, study)

        existing = await self._session.scalar(
            select(Instance).where(Instance.sop_instance_uid == meta.sop_instance_uid)
        )
        if existing is not None:
            # Idempotent re-ingest: refresh the object pointer + metadata.
            existing.object_key = object_key
            existing.size_bytes = size
            existing.transfer_syntax_uid = meta.transfer_syntax_uid
            await self._session.flush()
            return existing, False

        instance = Instance(
            sop_instance_uid=meta.sop_instance_uid,
            sop_class_uid=meta.sop_class_uid,
            instance_number=meta.instance_number,
            transfer_syntax_uid=meta.transfer_syntax_uid,
            rows=meta.rows,
            columns=meta.columns,
            object_key=object_key,
            size_bytes=size,
            series_pk=series.id,
        )
        self._session.add(instance)
        await self._session.flush()
        return instance, True

    async def _get_or_create_patient(self, meta: DicomMetadata) -> Patient:
        patient_id = meta.patient_id or "UNKNOWN"
        patient = await self._session.scalar(
            select(Patient).where(Patient.patient_id == patient_id)
        )
        if patient is None:
            patient = Patient(
                patient_id=patient_id,
                patient_name=meta.patient_name,
                birth_date=meta.patient_birth_date,
                sex=meta.patient_sex,
            )
            self._session.add(patient)
            await self._session.flush()
        return patient

    async def _get_or_create_study(self, meta: DicomMetadata, patient: Patient) -> Study:
        study = await self._session.scalar(
            select(Study).where(Study.study_instance_uid == meta.study_instance_uid)
        )
        if study is None:
            study = Study(
                study_instance_uid=meta.study_instance_uid,
                accession_number=meta.accession_number,
                study_date=meta.study_date,
                study_time=meta.study_time,
                description=meta.study_description,
                modalities=meta.modality,
                patient_pk=patient.id,
            )
            self._session.add(study)
            await self._session.flush()
        else:
            study.modalities = self._merge_modalities(study.modalities, meta.modality)
            await self._session.flush()
        return study

    async def _get_or_create_series(self, meta: DicomMetadata, study: Study) -> Series:
        series = await self._session.scalar(
            select(Series).where(Series.series_instance_uid == meta.series_instance_uid)
        )
        if series is None:
            series = Series(
                series_instance_uid=meta.series_instance_uid,
                modality=meta.modality,
                series_number=meta.series_number,
                description=meta.series_description,
                body_part=meta.body_part,
                study_pk=study.id,
            )
            self._session.add(series)
            await self._session.flush()
        return series

    @staticmethod
    def _merge_modalities(existing: str | None, new: str | None) -> str | None:
        parts = {p for p in (existing or "").split("\\") if p}
        if new:
            parts.add(new)
        return "\\".join(sorted(parts)) if parts else None
