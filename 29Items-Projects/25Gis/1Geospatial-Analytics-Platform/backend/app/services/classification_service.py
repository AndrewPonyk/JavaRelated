from uuid import UUID

from fastapi import status
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.models.dataset import MLClassificationJob
from app.schemas.dataset import ClassificationJobCreate, ClassificationJobUpdate
from app.services.dataset_service import DatasetService
from app.services.layer_service import LayerService


class ClassificationJobService:
    def __init__(self, db: Session) -> None:
        self.db = db

    def create(self, payload: ClassificationJobCreate) -> MLClassificationJob:
        DatasetService(self.db).require_dataset(payload.dataset_id)
        job = MLClassificationJob(
            dataset_id=payload.dataset_id,
            status="queued",
            model_version=payload.model_version,
            metrics={},
        )
        self.db.add(job)
        self.db.commit()
        self.db.refresh(job)
        return job

    def list(self, dataset_id: UUID | None = None, limit: int = 100, offset: int = 0) -> list[MLClassificationJob]:
        statement = select(MLClassificationJob).order_by(MLClassificationJob.created_at.desc())
        if dataset_id is not None:
            statement = statement.where(MLClassificationJob.dataset_id == dataset_id)
        statement = statement.limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def get(self, job_id: UUID) -> MLClassificationJob | None:
        return self.db.get(MLClassificationJob, job_id)

    def require_job(self, job_id: UUID) -> MLClassificationJob:
        job = self.get(job_id)
        if job is None:
            raise AppError("job_not_found", "Classification job not found", status.HTTP_404_NOT_FOUND)
        return job

    def update(self, job_id: UUID, payload: ClassificationJobUpdate) -> MLClassificationJob | None:
        job = self.get(job_id)
        if job is None:
            return None
        if job.status not in {"queued", "failed"}:
            raise AppError("job_locked", "Only queued or failed jobs can be updated", status.HTTP_400_BAD_REQUEST)
        for field, value in payload.model_dump(exclude_unset=True).items():
            setattr(job, field, value)
        self.db.commit()
        self.db.refresh(job)
        return job

    def run(self, job_id: UUID) -> MLClassificationJob:
        job = self.require_job(job_id)
        if job.dataset_id is None:
            raise AppError("job_without_dataset", "Classification job is not attached to a dataset")

        job.status = "running"
        self.db.commit()

        try:
            rows = self.db.execute(
                text(
                    """
                    SELECT COALESCE(properties->>'land_use', properties->>'class', properties->>'category', 'unclassified') AS class_name,
                           COUNT(*) AS count
                    FROM dataset_features
                    WHERE dataset_id = :dataset_id
                    GROUP BY class_name
                    ORDER BY class_name
                    """
                ),
                {"dataset_id": job.dataset_id},
            )
            class_counts = {str(row.class_name): int(row.count) for row in rows}
            total = sum(class_counts.values())
            class_share = {key: round(value / total, 4) for key, value in class_counts.items()} if total else {}
            job.metrics = {
                "feature_count": total,
                "class_counts": class_counts,
                "class_share": class_share,
                "method": "deterministic-property-classification",
            }
            job.status = "succeeded"
            job.artifact_uri = f"postgis://datasets/{job.dataset_id}/classification-jobs/{job.id}"
            job.error_message = None
            self.db.commit()
            self.db.refresh(job)

            existing_layers = LayerService(self.db).list(dataset_id=job.dataset_id, limit=1_000)
            if not any(layer.layer_type == "classification" for layer in existing_layers):
                from app.schemas.dataset import LayerCreate

                LayerService(self.db).create(
                    LayerCreate(
                        dataset_id=job.dataset_id,
                        name="Land use classification",
                        layer_type="classification",
                        style="land-use",
                        is_public=False,
                    )
                )
            return job
        except Exception as exc:
            job.status = "failed"
            job.error_message = str(exc)
            self.db.commit()
            self.db.refresh(job)
            raise

    def delete(self, job_id: UUID) -> bool:
        job = self.get(job_id)
        if job is None:
            return False
        self.db.delete(job)
        self.db.commit()
        return True
