from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.security import require_role
from app.db.session import SessionLocal, get_db
from app.schemas.dataset import ClassificationJobCreate, ClassificationJobRead, ClassificationJobUpdate
from app.services.classification_service import ClassificationJobService

router = APIRouter()


def run_job_in_new_session(job_id: UUID) -> None:
    with SessionLocal() as db:
        ClassificationJobService(db).run(job_id)


@router.post(
    "",
    response_model=ClassificationJobRead,
    status_code=status.HTTP_202_ACCEPTED,
    dependencies=[Depends(require_role("analyst"))],
)
def create_classification_job(
    payload: ClassificationJobCreate,
    background_tasks: BackgroundTasks,
    run_immediately: bool = Query(default=True),
    db: Session = Depends(get_db),
) -> ClassificationJobRead:
    job = ClassificationJobService(db).create(payload)
    if run_immediately:
        background_tasks.add_task(run_job_in_new_session, job.id)
    return job


@router.post("/{job_id}/run", response_model=ClassificationJobRead, dependencies=[Depends(require_role("analyst"))])
def run_classification_job(job_id: UUID, db: Session = Depends(get_db)) -> ClassificationJobRead:
    return ClassificationJobService(db).run(job_id)


@router.get("", response_model=list[ClassificationJobRead])
def list_classification_jobs(
    dataset_id: UUID | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> list[ClassificationJobRead]:
    return ClassificationJobService(db).list(dataset_id=dataset_id, limit=limit, offset=offset)


@router.get("/{job_id}", response_model=ClassificationJobRead)
def get_classification_job(job_id: UUID, db: Session = Depends(get_db)) -> ClassificationJobRead:
    job = ClassificationJobService(db).get(job_id)
    if job is None:
        raise AppError("job_not_found", "Classification job not found", status.HTTP_404_NOT_FOUND)
    return job


@router.patch("/{job_id}", response_model=ClassificationJobRead, dependencies=[Depends(require_role("analyst"))])
def update_classification_job(
    job_id: UUID,
    payload: ClassificationJobUpdate,
    db: Session = Depends(get_db),
) -> ClassificationJobRead:
    job = ClassificationJobService(db).update(job_id, payload)
    if job is None:
        raise AppError("job_not_found", "Classification job not found", status.HTTP_404_NOT_FOUND)
    return job


@router.delete("/{job_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Depends(require_role("analyst"))])
def delete_classification_job(job_id: UUID, db: Session = Depends(get_db)) -> None:
    deleted = ClassificationJobService(db).delete(job_id)
    if not deleted:
        raise AppError("job_not_found", "Classification job not found", status.HTTP_404_NOT_FOUND)
