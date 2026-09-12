"""Instance metadata + presigned frame access.

The viewer loads pixels by requesting a short-lived presigned URL and handing it
to Cornerstone's `wadouri:` loader, so the browser streams the DICOM object
directly from object storage (no pixel bytes through the API).
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.deps import ClientIp, CurrentUser, DbSession, Storage, require_scope
from app.core.config import settings
from app.core.security import Scope
from app.schemas.instance import InstanceFrameUrl, InstanceRead
from app.services.audit_service import AuditService
from app.services.instance_service import InstanceService

router = APIRouter()


@router.get(
    "/{sop_instance_uid}",
    response_model=InstanceRead,
    summary="Instance metadata by SOPInstanceUID",
)
async def get_instance(
    sop_instance_uid: str,
    session: DbSession,
    _: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_READ))],
) -> InstanceRead:
    instance = await InstanceService(session).get_by_sop_uid(sop_instance_uid)
    return InstanceRead.model_validate(instance)


@router.get(
    "/{sop_instance_uid}/frame-url",
    response_model=InstanceFrameUrl,
    summary="Short-lived presigned URL for the instance's DICOM object",
)
async def get_frame_url(
    sop_instance_uid: str,
    session: DbSession,
    storage: Storage,
    user: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_READ))],
    ip: ClientIp,
) -> InstanceFrameUrl:
    instance = await InstanceService(session).get_by_sop_uid(sop_instance_uid)
    url = storage.presigned_get(settings.s3_bucket_dicom, instance.object_key)
    await AuditService(session).record(
        actor_id=user.sub,
        action="read",
        resource_type="instance",
        resource_id=sop_instance_uid,
        source_ip=ip,
    )
    return InstanceFrameUrl(
        sop_instance_uid=sop_instance_uid,
        url=url,
        expires_in_seconds=settings.presign_expiry_seconds,
    )
