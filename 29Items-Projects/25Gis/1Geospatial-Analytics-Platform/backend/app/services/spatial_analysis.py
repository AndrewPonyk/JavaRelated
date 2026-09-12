from dataclasses import dataclass
from uuid import UUID

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.schemas.dataset import DatasetFeatureRead, DatasetSummary
from app.services.dataset_service import DatasetService, _feature_from_mapping


@dataclass(frozen=True)
class BoundingBox:
    min_x: float
    min_y: float
    max_x: float
    max_y: float
    srid: int = 4326


class SpatialAnalysisService:
    def __init__(self, db: Session) -> None:
        self.db = db

    def search_bbox(self, dataset_id: UUID, bbox: BoundingBox, limit: int = 500) -> list[DatasetFeatureRead]:
        DatasetService(self.db).require_dataset(dataset_id)
        result = self.db.execute(
            text(
                """
                SELECT id, dataset_id, properties, ST_AsGeoJSON(geom)::json AS geometry, created_at
                FROM dataset_features
                WHERE dataset_id = :dataset_id
                  AND ST_Intersects(geom, ST_Transform(ST_MakeEnvelope(:min_x, :min_y, :max_x, :max_y, :srid), 4326))
                ORDER BY created_at DESC
                LIMIT :limit
                """
            ),
            {
                "dataset_id": dataset_id,
                "min_x": bbox.min_x,
                "min_y": bbox.min_y,
                "max_x": bbox.max_x,
                "max_y": bbox.max_y,
                "srid": bbox.srid,
                "limit": limit,
            },
        )
        return [_feature_from_mapping(row) for row in result]

    def search_proximity(
        self,
        dataset_id: UUID,
        longitude: float,
        latitude: float,
        radius_meters: float,
        limit: int = 500,
    ) -> list[DatasetFeatureRead]:
        DatasetService(self.db).require_dataset(dataset_id)
        result = self.db.execute(
            text(
                """
                SELECT id, dataset_id, properties, ST_AsGeoJSON(geom)::json AS geometry, created_at
                FROM dataset_features
                WHERE dataset_id = :dataset_id
                  AND ST_DWithin(
                    geom::geography,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    :radius_meters
                  )
                ORDER BY ST_Distance(
                    geom::geography,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                  )
                LIMIT :limit
                """
            ),
            {
                "dataset_id": dataset_id,
                "longitude": longitude,
                "latitude": latitude,
                "radius_meters": radius_meters,
                "limit": limit,
            },
        )
        return [_feature_from_mapping(row) for row in result]

    def summarize_dataset(self, dataset_id: UUID) -> DatasetSummary:
        DatasetService(self.db).require_dataset(dataset_id)
        feature_count = self.db.execute(
            text("SELECT COUNT(*) FROM dataset_features WHERE dataset_id = :dataset_id"),
            {"dataset_id": dataset_id},
        ).scalar_one()

        extent_text = self.db.execute(
            text(
                """
                SELECT ST_AsGeoJSON(ST_SetSRID(ST_Extent(geom)::geometry, 4326))::json
                FROM dataset_features
                WHERE dataset_id = :dataset_id
                """
            ),
            {"dataset_id": dataset_id},
        ).scalar_one_or_none()

        class_rows = self.db.execute(
            text(
                """
                SELECT COALESCE(properties->>'land_use', properties->>'class', 'unclassified') AS class_name,
                       COUNT(*) AS count
                FROM dataset_features
                WHERE dataset_id = :dataset_id
                GROUP BY class_name
                ORDER BY class_name
                """
            ),
            {"dataset_id": dataset_id},
        )
        return DatasetSummary(
            dataset_id=dataset_id,
            feature_count=int(feature_count),
            extent=extent_text,
            classes={str(row.class_name): int(row.count) for row in class_rows},
        )
