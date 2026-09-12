import json
from uuid import UUID

from fastapi import status
from sqlalchemy import delete, select, text
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.models.dataset import Dataset, DatasetFeature
from app.schemas.dataset import (
    DatasetCreate,
    DatasetFeatureCreate,
    DatasetFeatureRead,
    DatasetFeatureUpdate,
    DatasetUpdate,
    GeoJsonFeatureCollection,
)


def _coerce_geometry(value: object) -> dict:
    if isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError as exc:
            raise AppError("invalid_geometry", "Database returned invalid GeoJSON") from exc
    if isinstance(value, dict):
        return value
    raise AppError("invalid_geometry", "Database returned an unsupported geometry value")


def _feature_from_mapping(row: object) -> DatasetFeatureRead:
    mapping = dict(row._mapping)  # type: ignore[attr-defined]
    mapping["geometry"] = _coerce_geometry(mapping["geometry"])
    return DatasetFeatureRead(**mapping)


class DatasetService:
    def __init__(self, db: Session) -> None:
        self.db = db

    def create(self, payload: DatasetCreate) -> Dataset:
        dataset = Dataset(**payload.model_dump())
        self.db.add(dataset)
        self.db.commit()
        self.db.refresh(dataset)
        return dataset

    def list(self, limit: int = 100, offset: int = 0) -> list[Dataset]:
        statement = select(Dataset).order_by(Dataset.created_at.desc()).limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def get(self, dataset_id: UUID) -> Dataset | None:
        return self.db.get(Dataset, dataset_id)

    def update(self, dataset_id: UUID, payload: DatasetUpdate) -> Dataset | None:
        dataset = self.get(dataset_id)
        if dataset is None:
            return None

        for field, value in payload.model_dump(exclude_unset=True).items():
            setattr(dataset, field, value)

        self.db.commit()
        self.db.refresh(dataset)
        return dataset

    def delete(self, dataset_id: UUID) -> bool:
        dataset = self.get(dataset_id)
        if dataset is None:
            return False

        self.db.delete(dataset)
        self.db.commit()
        return True

    def require_dataset(self, dataset_id: UUID) -> Dataset:
        dataset = self.get(dataset_id)
        if dataset is None:
            raise AppError("dataset_not_found", "Dataset not found", status.HTTP_404_NOT_FOUND)
        return dataset


class DatasetFeatureService:
    def __init__(self, db: Session) -> None:
        self.db = db

    def create(self, dataset_id: UUID, payload: DatasetFeatureCreate) -> DatasetFeatureRead:
        DatasetService(self.db).require_dataset(dataset_id)
        result = self.db.execute(
            text(
                """
                INSERT INTO dataset_features (dataset_id, properties, geom)
                VALUES (:dataset_id, CAST(:properties AS jsonb), ST_SetSRID(ST_GeomFromGeoJSON(:geometry), 4326))
                RETURNING id, dataset_id, properties, ST_AsGeoJSON(geom)::json AS geometry, created_at
                """
            ),
            {
                "dataset_id": dataset_id,
                "properties": json.dumps(payload.properties),
                "geometry": json.dumps(payload.geometry),
            },
        )
        self.db.commit()
        return _feature_from_mapping(result.one())

    def bulk_import_geojson(self, dataset_id: UUID, payload: GeoJsonFeatureCollection) -> list[DatasetFeatureRead]:
        DatasetService(self.db).require_dataset(dataset_id)
        created: list[DatasetFeatureRead] = []
        for raw_feature in payload.features:
            if raw_feature.get("type") != "Feature":
                raise AppError("invalid_geojson_feature", "Every imported item must be a GeoJSON Feature")
            feature = DatasetFeatureCreate(
                properties=raw_feature.get("properties") or {},
                geometry=raw_feature.get("geometry") or {},
            )
            created.append(self.create(dataset_id, feature))
        return created

    def list(self, dataset_id: UUID, limit: int = 500, offset: int = 0) -> list[DatasetFeatureRead]:
        DatasetService(self.db).require_dataset(dataset_id)
        result = self.db.execute(
            text(
                """
                SELECT id, dataset_id, properties, ST_AsGeoJSON(geom)::json AS geometry, created_at
                FROM dataset_features
                WHERE dataset_id = :dataset_id
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """
            ),
            {"dataset_id": dataset_id, "limit": limit, "offset": offset},
        )
        return [_feature_from_mapping(row) for row in result]

    def get(self, feature_id: UUID) -> DatasetFeatureRead | None:
        result = self.db.execute(
            text(
                """
                SELECT id, dataset_id, properties, ST_AsGeoJSON(geom)::json AS geometry, created_at
                FROM dataset_features
                WHERE id = :feature_id
                """
            ),
            {"feature_id": feature_id},
        ).first()
        return None if result is None else _feature_from_mapping(result)

    def update(self, feature_id: UUID, payload: DatasetFeatureUpdate) -> DatasetFeatureRead | None:
        feature = self.db.get(DatasetFeature, feature_id)
        if feature is None:
            return None

        if payload.properties is not None:
            self.db.execute(
                text("UPDATE dataset_features SET properties = CAST(:properties AS jsonb) WHERE id = :feature_id"),
                {"feature_id": feature_id, "properties": json.dumps(payload.properties)},
            )
        if payload.geometry is not None:
            self.db.execute(
                text(
                    """
                    UPDATE dataset_features
                    SET geom = ST_SetSRID(ST_GeomFromGeoJSON(:geometry), 4326)
                    WHERE id = :feature_id
                    """
                ),
                {"feature_id": feature_id, "geometry": json.dumps(payload.geometry)},
            )
        self.db.commit()
        return self.get(feature_id)

    def delete(self, feature_id: UUID) -> bool:
        result = self.db.execute(delete(DatasetFeature).where(DatasetFeature.id == feature_id))
        self.db.commit()
        return bool(result.rowcount)
