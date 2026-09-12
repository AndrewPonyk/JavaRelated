"""DICOMweb endpoints (PS3.18): STOW-RS, QIDO-RS, WADO-RS.

Standard HTTP interfaces so modalities and the Cornerstone viewer interoperate
without bespoke protocols.
  - STOW-RS  POST  /studies            store instances
  - QIDO-RS  GET   /studies            query studies
  - WADO-RS  GET   /studies/{...}       retrieve an instance
"""

from __future__ import annotations

from typing import Annotated, Any

from fastapi import APIRouter, Depends, Request, Response, status
from fastapi.responses import JSONResponse

from app.api.deps import ClientIp, CurrentUser, DbSession, Storage, require_scope
from app.core.config import settings
from app.core.exceptions import InvalidDicomError
from app.core.security import Scope
from app.schemas.study import StudyQuery
from app.services.audit_service import AuditService
from app.services.dicomweb_util import (
    DicomJson,
    parse_multipart_related,
    reference_sop,
    study_to_dicom_json,
)
from app.services.ingestion_service import IngestionService
from app.services.instance_service import InstanceService
from app.services.storage_service import StorageService
from app.services.study_service import StudyService

router = APIRouter()


@router.post("/studies", summary="STOW-RS: store DICOM instances")
async def stow_store(
    request: Request,
    session: DbSession,
    storage: Storage,
    user: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_WRITE))],
    ip: ClientIp,
) -> JSONResponse:
    """Accept `multipart/related; type="application/dicom"` (or a single object).

    In inline mode (dev) each instance is ingested synchronously; otherwise it is
    staged and an ingest job is enqueued for the worker.
    """
    body = await request.body()
    content_type = request.headers.get("content-type", "")
    parts = parse_multipart_related(body, content_type)

    ingestion = IngestionService(session, storage)
    referenced: list[DicomJson] = []
    failed: list[DicomJson] = []

    for raw in parts:
        try:
            if settings.ingest_inline:
                result = await ingestion.ingest(raw, actor_id=user.sub)
                referenced.append(
                    reference_sop(
                        result.study_instance_uid,
                        result.series_instance_uid,
                        result.sop_instance_uid,
                        result.sop_class_uid,
                    )
                )
            else:
                key = await _stage_and_enqueue(storage, raw)
                referenced.append({"00081190": {"vr": "UR", "Value": [key]}})
        except InvalidDicomError:
            failed.append({"00081197": {"vr": "US", "Value": [43264]}})  # processing failure

    await AuditService(session).record(
        actor_id=user.sub, action="create", resource_type="instance", source_ip=ip
    )

    response_body: dict[str, Any] = {"00081199": {"vr": "SQ", "Value": referenced}}
    if failed:
        response_body["00081198"] = {"vr": "SQ", "Value": failed}
    code = status.HTTP_200_OK if referenced else status.HTTP_409_CONFLICT
    return JSONResponse(status_code=code, content=response_body)


async def _stage_and_enqueue(storage: StorageService, raw: bytes) -> str:
    """Queue-mode path: store to staging and push an ingest job."""
    import uuid

    from app.services.queue_service import get_queue_client

    key = f"staging/{uuid.uuid4()}.dcm"
    storage.put_object(settings.s3_bucket_staging, key, raw, content_type="application/dicom")
    get_queue_client().send({"type": "ingest", "object_key": key})
    return key


@router.get("/studies", summary="QIDO-RS: search for studies")
async def qido_search_studies(
    session: DbSession,
    user: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_READ))],
    ip: ClientIp,
    PatientID: str | None = None,
    AccessionNumber: str | None = None,
    StudyDate: str | None = None,
    ModalitiesInStudy: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[DicomJson]:
    """Return matching studies as DICOM JSON (PS3.18 §6.7)."""
    query = StudyQuery(
        patient_id=PatientID,
        accession_number=AccessionNumber,
        study_date_from=StudyDate,
        study_date_to=StudyDate,
        modality=ModalitiesInStudy,
        limit=min(max(limit, 1), 200),
        offset=max(offset, 0),
    )
    rows = await StudyService(session).qido_rows(query)
    await AuditService(session).record(
        actor_id=user.sub, action="read", resource_type="study", source_ip=ip
    )
    return [
        study_to_dicom_json(
            study_instance_uid=s.study_instance_uid,
            accession_number=s.accession_number,
            study_date=s.study_date,
            description=s.description,
            modalities=s.modalities,
            patient_id=pid,
            patient_name=pname,
            series_count=count,
        )
        for s, count, pid, pname in rows
    ]


@router.get(
    "/studies/{study_uid}/series/{series_uid}/instances/{sop_uid}",
    summary="WADO-RS: retrieve a single instance (application/dicom)",
)
async def wado_retrieve_instance(
    study_uid: str,
    series_uid: str,
    sop_uid: str,
    session: DbSession,
    storage: Storage,
    user: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_READ))],
    ip: ClientIp,
) -> Response:
    """Stream the stored Part-10 object back to the caller."""
    instance = await InstanceService(session).get_by_sop_uid(sop_uid)
    data = storage.get_object(settings.s3_bucket_dicom, instance.object_key)
    await AuditService(session).record(
        actor_id=user.sub,
        action="read",
        resource_type="instance",
        resource_id=sop_uid,
        source_ip=ip,
    )
    return Response(content=data, media_type="application/dicom")
