from uuid import UUID

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.security import require_role
from app.db.session import get_db
from app.schemas.dataset import LayerCreate, LayerRead, LayerUpdate
from app.services.layer_service import LayerService

router = APIRouter()


@router.post("", response_model=LayerRead, status_code=status.HTTP_201_CREATED, dependencies=[Depends(require_role("analyst"))])
def create_layer(payload: LayerCreate, db: Session = Depends(get_db)) -> LayerRead:
    return LayerService(db).create(payload)


@router.get("", response_model=list[LayerRead])
def list_layers(
    dataset_id: UUID | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> list[LayerRead]:
    return LayerService(db).list(dataset_id=dataset_id, limit=limit, offset=offset)


@router.get("/{layer_id}", response_model=LayerRead)
def get_layer(layer_id: UUID, db: Session = Depends(get_db)) -> LayerRead:
    layer = LayerService(db).get(layer_id)
    if layer is None:
        raise AppError("layer_not_found", "Layer not found", status.HTTP_404_NOT_FOUND)
    return layer


@router.patch("/{layer_id}", response_model=LayerRead, dependencies=[Depends(require_role("analyst"))])
def update_layer(layer_id: UUID, payload: LayerUpdate, db: Session = Depends(get_db)) -> LayerRead:
    layer = LayerService(db).update(layer_id, payload)
    if layer is None:
        raise AppError("layer_not_found", "Layer not found", status.HTTP_404_NOT_FOUND)
    return layer


@router.delete("/{layer_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Depends(require_role("analyst"))])
def delete_layer(layer_id: UUID, db: Session = Depends(get_db)) -> None:
    deleted = LayerService(db).delete(layer_id)
    if not deleted:
        raise AppError("layer_not_found", "Layer not found", status.HTTP_404_NOT_FOUND)
