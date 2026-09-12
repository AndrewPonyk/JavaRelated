"""ML diagnostic-assist endpoints: trigger inference + fetch results."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.deps import ClientIp, CurrentUser, DbSession, Storage, require_scope
from app.core.config import settings
from app.core.security import Scope
from app.schemas.ml import MlPrediction, MlTriggerRequest, MlTriggerResponse
from app.services.audit_service import AuditService
from app.services.dicom_service import CHEST_XRAY_MODALITIES
from app.services.instance_service import InstanceService
from app.services.ml_service import MlService

router = APIRouter()


@router.post(
    "/trigger",
    response_model=MlTriggerResponse,
    summary="Run/queue chest X-ray classification for an instance",
)
async def trigger_inference(
    body: MlTriggerRequest,
    session: DbSession,
    user: Annotated[CurrentUser, Depends(require_scope(Scope.ML_TRIGGER))],
    ip: ClientIp,
) -> MlTriggerResponse:
    isvc = InstanceService(session)
    instance, modality = await isvc.get_with_modality(body.sop_instance_uid)

    if (modality or "").upper() not in CHEST_XRAY_MODALITIES:
        return MlTriggerResponse(
            sop_instance_uid=body.sop_instance_uid, status="skipped_unsupported_modality"
        )

    ml = MlService(session)
    existing = await ml.get_for_instance(instance.id)
    if existing is not None and not body.force:
        status = "exists"
    elif settings.ml_inline:
        result = await ml.infer_and_store(instance)
        status = "completed" if result is not None else "failed"
    else:
        ml.enqueue(instance)
        status = "queued"

    await AuditService(session).record(
        actor_id=user.sub,
        action="create",
        resource_type="ml",
        resource_id=body.sop_instance_uid,
        source_ip=ip,
    )
    return MlTriggerResponse(sop_instance_uid=body.sop_instance_uid, status=status)


@router.get(
    "/results/{study_instance_uid}",
    response_model=list[MlPrediction],
    summary="Fetch ML predictions for a study",
)
async def get_results(
    study_instance_uid: str,
    session: DbSession,
    storage: Storage,
    user: Annotated[CurrentUser, Depends(require_scope(Scope.ML_READ))],
    ip: ClientIp,
) -> list[MlPrediction]:
    results = await MlService(session).list_for_study(study_instance_uid)
    await AuditService(session).record(
        actor_id=user.sub,
        action="read",
        resource_type="ml",
        resource_id=study_instance_uid,
        source_ip=ip,
    )
    out: list[MlPrediction] = []
    for r in results:
        heatmap_url = (
            storage.presigned_get(settings.s3_bucket_dicom, r.heatmap_key)
            if r.heatmap_key
            else None
        )
        out.append(
            MlPrediction(
                id=r.id,
                model_name=r.model_name,
                model_version=r.model_version,
                predictions=r.predictions,
                top_label=r.top_label,
                top_score=r.top_score,
                heatmap_url=heatmap_url,
                created_at=r.created_at,
            )
        )
    return out
