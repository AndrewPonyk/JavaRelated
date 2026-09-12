from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.db.session import get_db
from app.schemas.dataset import DatasetFeatureRead, DatasetSummary
from app.services.spatial_analysis import BoundingBox, SpatialAnalysisService

router = APIRouter()


@router.get("/datasets/{dataset_id}/bbox", response_model=list[DatasetFeatureRead])
def search_bbox(
    dataset_id: UUID,
    min_x: float,
    min_y: float,
    max_x: float,
    max_y: float,
    srid: int = Query(default=4326, ge=1),
    limit: int = Query(default=500, ge=1, le=2_000),
    db: Session = Depends(get_db),
) -> list[DatasetFeatureRead]:
    if max_x <= min_x or max_y <= min_y:
        raise AppError("invalid_bbox", "max_x/max_y must be greater than min_x/min_y")
    bbox = BoundingBox(min_x=min_x, min_y=min_y, max_x=max_x, max_y=max_y, srid=srid)
    return SpatialAnalysisService(db).search_bbox(dataset_id, bbox, limit=limit)


@router.get("/datasets/{dataset_id}/proximity", response_model=list[DatasetFeatureRead])
def search_proximity(
    dataset_id: UUID,
    longitude: float = Query(ge=-180, le=180),
    latitude: float = Query(ge=-90, le=90),
    radius_meters: float = Query(gt=0, le=100_000),
    limit: int = Query(default=500, ge=1, le=2_000),
    db: Session = Depends(get_db),
) -> list[DatasetFeatureRead]:
    return SpatialAnalysisService(db).search_proximity(
        dataset_id=dataset_id,
        longitude=longitude,
        latitude=latitude,
        radius_meters=radius_meters,
        limit=limit,
    )


@router.get("/datasets/{dataset_id}/summary", response_model=DatasetSummary)
def summarize_dataset(dataset_id: UUID, db: Session = Depends(get_db)) -> DatasetSummary:
    return SpatialAnalysisService(db).summarize_dataset(dataset_id)
