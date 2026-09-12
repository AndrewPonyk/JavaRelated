from uuid import UUID

from fastapi import status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.models.dataset import Layer
from app.schemas.dataset import LayerCreate, LayerUpdate
from app.services.dataset_service import DatasetService


class LayerService:
    def __init__(self, db: Session) -> None:
        self.db = db

    def create(self, payload: LayerCreate) -> Layer:
        DatasetService(self.db).require_dataset(payload.dataset_id)
        layer = Layer(**payload.model_dump())
        self.db.add(layer)
        self.db.commit()
        self.db.refresh(layer)
        return layer

    def list(self, dataset_id: UUID | None = None, limit: int = 100, offset: int = 0) -> list[Layer]:
        statement = select(Layer).order_by(Layer.created_at.desc())
        if dataset_id is not None:
            statement = statement.where(Layer.dataset_id == dataset_id)
        statement = statement.limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def get(self, layer_id: UUID) -> Layer | None:
        return self.db.get(Layer, layer_id)

    def require_layer(self, layer_id: UUID) -> Layer:
        layer = self.get(layer_id)
        if layer is None:
            raise AppError("layer_not_found", "Layer not found", status.HTTP_404_NOT_FOUND)
        return layer

    def update(self, layer_id: UUID, payload: LayerUpdate) -> Layer | None:
        layer = self.get(layer_id)
        if layer is None:
            return None
        for field, value in payload.model_dump(exclude_unset=True).items():
            setattr(layer, field, value)
        self.db.commit()
        self.db.refresh(layer)
        return layer

    def delete(self, layer_id: UUID) -> bool:
        layer = self.get(layer_id)
        if layer is None:
            return False
        self.db.delete(layer)
        self.db.commit()
        return True
