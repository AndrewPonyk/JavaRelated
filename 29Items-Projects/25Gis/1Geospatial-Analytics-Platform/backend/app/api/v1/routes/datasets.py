from uuid import UUID

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.security import require_role
from app.db.session import get_db
from app.schemas.dataset import (
    DatasetCreate,
    DatasetFeatureCreate,
    DatasetFeatureRead,
    DatasetFeatureUpdate,
    DatasetRead,
    DatasetUpdate,
    GeoJsonFeatureCollection,
)
from app.services.dataset_service import DatasetFeatureService, DatasetService

router = APIRouter()


@router.post("", response_model=DatasetRead, status_code=status.HTTP_201_CREATED, dependencies=[Depends(require_role("analyst"))])
def create_dataset(payload: DatasetCreate, db: Session = Depends(get_db)) -> DatasetRead:
    service = DatasetService(db)
    return service.create(payload)


@router.get("", response_model=list[DatasetRead])
def list_datasets(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> list[DatasetRead]:
    service = DatasetService(db)
    return service.list(limit=limit, offset=offset)


@router.get("/{dataset_id}", response_model=DatasetRead)
def get_dataset(dataset_id: UUID, db: Session = Depends(get_db)) -> DatasetRead:
    service = DatasetService(db)
    dataset = service.get(dataset_id)
    if dataset is None:
        raise AppError("dataset_not_found", "Dataset not found", status.HTTP_404_NOT_FOUND)
    return dataset


@router.patch("/{dataset_id}", response_model=DatasetRead, dependencies=[Depends(require_role("analyst"))])
def update_dataset(dataset_id: UUID, payload: DatasetUpdate, db: Session = Depends(get_db)) -> DatasetRead:
    service = DatasetService(db)
    dataset = service.update(dataset_id, payload)
    if dataset is None:
        raise AppError("dataset_not_found", "Dataset not found", status.HTTP_404_NOT_FOUND)
    return dataset


@router.delete("/{dataset_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Depends(require_role("analyst"))])
def delete_dataset(dataset_id: UUID, db: Session = Depends(get_db)) -> None:
    service = DatasetService(db)
    deleted = service.delete(dataset_id)
    if not deleted:
        raise AppError("dataset_not_found", "Dataset not found", status.HTTP_404_NOT_FOUND)


@router.post(
    "/{dataset_id}/features",
    response_model=DatasetFeatureRead,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_role("analyst"))],
)
def create_feature(dataset_id: UUID, payload: DatasetFeatureCreate, db: Session = Depends(get_db)) -> DatasetFeatureRead:
    return DatasetFeatureService(db).create(dataset_id, payload)


@router.post(
    "/{dataset_id}/features/import-geojson",
    response_model=list[DatasetFeatureRead],
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_role("analyst"))],
)
def import_geojson_features(
    dataset_id: UUID,
    payload: GeoJsonFeatureCollection,
    db: Session = Depends(get_db),
) -> list[DatasetFeatureRead]:
    return DatasetFeatureService(db).bulk_import_geojson(dataset_id, payload)


@router.get("/{dataset_id}/features", response_model=list[DatasetFeatureRead])
def list_features(
    dataset_id: UUID,
    limit: int = Query(default=500, ge=1, le=2_000),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> list[DatasetFeatureRead]:
    return DatasetFeatureService(db).list(dataset_id, limit=limit, offset=offset)


@router.get("/features/{feature_id}", response_model=DatasetFeatureRead)
def get_feature(feature_id: UUID, db: Session = Depends(get_db)) -> DatasetFeatureRead:
    feature = DatasetFeatureService(db).get(feature_id)
    if feature is None:
        raise AppError("feature_not_found", "Feature not found", status.HTTP_404_NOT_FOUND)
    return feature


@router.patch(
    "/features/{feature_id}",
    response_model=DatasetFeatureRead,
    dependencies=[Depends(require_role("analyst"))],
)
def update_feature(feature_id: UUID, payload: DatasetFeatureUpdate, db: Session = Depends(get_db)) -> DatasetFeatureRead:
    feature = DatasetFeatureService(db).update(feature_id, payload)
    if feature is None:
        raise AppError("feature_not_found", "Feature not found", status.HTTP_404_NOT_FOUND)
    return feature


@router.delete(
    "/features/{feature_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(require_role("analyst"))],
)
def delete_feature(feature_id: UUID, db: Session = Depends(get_db)) -> None:
    deleted = DatasetFeatureService(db).delete(feature_id)
    if not deleted:
        raise AppError("feature_not_found", "Feature not found", status.HTTP_404_NOT_FOUND)
