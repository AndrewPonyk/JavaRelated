"""Study worklist endpoints (REST).

Standard endpoint shape: validate input → enforce scope → delegate to a service
→ audit PHI access → return typed response.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Query

from app.api.deps import ClientIp, CurrentUser, DbSession, require_scope
from app.core.security import Scope
from app.models.study import Study
from app.schemas.instance import InstanceRead
from app.schemas.series import SeriesRead
from app.schemas.study import PageMeta, StudyPage, StudyQuery, StudyRead
from app.services.audit_service import AuditService
from app.services.instance_service import InstanceService
from app.services.study_service import StudyService

router = APIRouter()


def _to_read(study: Study, series_count: int) -> StudyRead:
    return StudyRead(
        id=study.id,
        study_instance_uid=study.study_instance_uid,
        accession_number=study.accession_number,
        study_date=study.study_date,
        description=study.description,
        modalities=study.modalities,
        series_count=series_count,
        created_at=study.created_at,
    )


@router.get("", response_model=StudyPage, summary="Query the study worklist")
async def list_studies(
    session: DbSession,
    user: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_READ))],
    ip: ClientIp,
    query: Annotated[StudyQuery, Query()],
) -> StudyPage:
    rows, total = await StudyService(session).search(query)
    await AuditService(session).record(
        actor_id=user.sub, action="read", resource_type="study", source_ip=ip
    )
    return StudyPage(
        items=[_to_read(s, c) for s, c in rows],
        meta=PageMeta(total=total, limit=query.limit, offset=query.offset),
    )


@router.get(
    "/{study_instance_uid}",
    response_model=StudyRead,
    summary="Fetch a single study by StudyInstanceUID",
)
async def get_study(
    study_instance_uid: str,
    session: DbSession,
    user: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_READ))],
    ip: ClientIp,
) -> StudyRead:
    study, count = await StudyService(session).get_by_uid(study_instance_uid)
    await AuditService(session).record(
        actor_id=user.sub,
        action="read",
        resource_type="study",
        resource_id=study_instance_uid,
        source_ip=ip,
    )
    return _to_read(study, count)


@router.get(
    "/{study_instance_uid}/series",
    response_model=list[SeriesRead],
    summary="List the series of a study",
)
async def list_series(
    study_instance_uid: str,
    session: DbSession,
    _: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_READ))],
) -> list[SeriesRead]:
    rows = await InstanceService(session).list_series_for_study(study_instance_uid)
    return [
        SeriesRead(
            id=s.id,
            series_instance_uid=s.series_instance_uid,
            modality=s.modality,
            series_number=s.series_number,
            description=s.description,
            body_part=s.body_part,
            instance_count=count,
        )
        for s, count in rows
    ]


@router.get(
    "/{study_instance_uid}/instances",
    response_model=list[InstanceRead],
    summary="List the instances of a study (ordered for stack display)",
)
async def list_instances(
    study_instance_uid: str,
    session: DbSession,
    _: Annotated[CurrentUser, Depends(require_scope(Scope.STUDY_READ))],
) -> list[InstanceRead]:
    instances = await InstanceService(session).list_for_study(study_instance_uid)
    return [InstanceRead.model_validate(i) for i in instances]
