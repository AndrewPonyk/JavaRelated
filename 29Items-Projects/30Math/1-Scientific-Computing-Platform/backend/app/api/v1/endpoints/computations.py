"""Saved-computation CRUD + artifact serving. All routes require
authentication; ownership scoping happens in the service layer (no
cross-tenant reads by construction).
"""

from uuid import UUID

from fastapi import APIRouter, HTTPException, Query, Response, status
from fastapi.responses import RedirectResponse

from app.api.deps import CurrentUser, SessionDep
from app.schemas.common import Page
from app.schemas.computation import ComputationCreate, ComputationRead
from app.services import computation_service
from app.services.artifact_store import (
    ArtifactRef,
    LocalArtifactStore,
    S3ArtifactStore,
    get_artifact_store,
)

router = APIRouter()


@router.post("", response_model=ComputationRead, status_code=status.HTTP_201_CREATED)
async def create_computation(
    payload: ComputationCreate, user: CurrentUser, session: SessionDep
) -> ComputationRead:
    computation = await computation_service.create(session, owner_id=user.user_id, data=payload)
    return ComputationRead.model_validate(computation)


@router.get("", response_model=Page[ComputationRead])
async def list_computations(
    user: CurrentUser,
    session: SessionDep,
    limit: int = Query(default=20, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
) -> Page[ComputationRead]:
    items, total = await computation_service.list_for_owner(
        session, owner_id=user.user_id, limit=limit, offset=offset
    )
    return Page(
        items=[ComputationRead.model_validate(c) for c in items],
        total=total,
        limit=limit,
        offset=offset,
    )


@router.get("/{computation_id}", response_model=ComputationRead)
async def get_computation(
    computation_id: UUID, user: CurrentUser, session: SessionDep
) -> ComputationRead:
    computation = await computation_service.get(
        session, owner_id=user.user_id, computation_id=computation_id
    )
    if computation is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Computation not found.")
    return ComputationRead.model_validate(computation)


@router.get("/{computation_id}/artifact")
async def get_computation_artifact(computation_id: UUID, user: CurrentUser, session: SessionDep):
    """Serve a computation's artifact (e.g. the rendered plot SVG).

    Local store → bytes straight from the shared volume; S3 store → redirect
    to a short-lived pre-signed URL.
    """
    computation = await computation_service.get(
        session, owner_id=user.user_id, computation_id=computation_id
    )
    if computation is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Computation not found.")
    artifact_payload = (computation.result_payload or {}).get("artifact")
    if not artifact_payload:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Computation has no artifact.")

    ref = ArtifactRef.from_payload(artifact_payload)
    store = get_artifact_store()
    if isinstance(store, S3ArtifactStore) and ref.storage == "s3":
        return RedirectResponse(store.presigned_url(ref), status_code=307)
    if isinstance(store, LocalArtifactStore) and ref.storage == "local":
        try:
            data = store.read(ref)
        except (FileNotFoundError, ValueError) as exc:
            raise HTTPException(
                status.HTTP_404_NOT_FOUND, detail="Artifact data is missing."
            ) from exc
        return Response(
            content=data,
            media_type=ref.content_type,
            headers={"Cache-Control": "private, max-age=3600"},
        )
    raise HTTPException(
        status.HTTP_409_CONFLICT,
        detail="Artifact was stored with a different backend than is configured.",
    )


@router.delete("/{computation_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_computation(computation_id: UUID, user: CurrentUser, session: SessionDep) -> None:
    deleted = await computation_service.delete(
        session, owner_id=user.user_id, computation_id=computation_id
    )
    if not deleted:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Computation not found.")
