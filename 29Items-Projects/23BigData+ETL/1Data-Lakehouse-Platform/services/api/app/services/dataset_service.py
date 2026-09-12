"""Dataset business logic. Routers stay thin: HTTP in, HTTP out — everything
else (rules, audit) happens here against the session. Domain errors are raised
as typed exceptions and mapped to problem responses in app.main.

Concurrency: uniqueness pre-checks give friendly errors on the common path,
and IntegrityError is mapped on commit so races surface as 409s, never 500s.
Audit rows commit atomically with the change; Kafka fan-out happens strictly
*after* a successful commit so consumers never see phantom events.
"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.db.models import AuditEvent, Dataset, DatasetLayer, DatasetVersion
from app.schemas.dataset import DatasetCreate, DatasetUpdate, DatasetVersionCreate
from app.services.audit_publisher import get_audit_publisher


class DatasetNotFoundError(Exception):
    pass


class DuplicateDatasetError(Exception):
    pass


class ConcurrentUpdateError(Exception):
    """Two writers raced on the same unique key — the request is retryable."""


def _stage_audit(
    session: Session, actor: str, action: str, dataset: Dataset, details: dict | None = None
) -> dict[str, Any]:
    """Add the audit row to the current transaction; return the fan-out payload."""
    session.add(
        AuditEvent(
            actor=actor,
            action=action,
            entity_type="dataset",
            entity_id=str(dataset.id),
            details=details,
        )
    )
    return {
        "actor": actor,
        "action": action,
        "entity_type": "dataset",
        "entity_id": str(dataset.id),
        "dataset_name": dataset.name,
        "details": details or {},
    }


def _commit_and_publish(session: Session, event: dict[str, Any]) -> None:
    """Commit the transaction, then fan the audit event out (best-effort)."""
    session.commit()
    get_audit_publisher().publish(event)


# --- Datasets -----------------------------------------------------------------


def list_datasets(
    session: Session,
    layer: str | None = None,
    name: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> tuple[list[Dataset], int]:
    query = select(Dataset)
    if layer:
        query = query.where(Dataset.layer == DatasetLayer(layer))
    if name:
        query = query.where(Dataset.name == name)
    total = session.scalar(select(func.count()).select_from(query.subquery())) or 0
    rows = session.scalars(query.order_by(Dataset.name).limit(limit).offset(offset)).all()
    return list(rows), total


def get_dataset(session: Session, dataset_id: uuid.UUID) -> Dataset:
    dataset = session.get(Dataset, dataset_id)
    if dataset is None:
        raise DatasetNotFoundError(f"dataset {dataset_id} does not exist")
    return dataset


def create_dataset(session: Session, payload: DatasetCreate, actor: str) -> Dataset:
    exists = session.scalar(select(Dataset).where(Dataset.name == payload.name))
    if exists:
        raise DuplicateDatasetError(payload.name)

    dataset = Dataset(
        name=payload.name,
        layer=DatasetLayer(payload.layer),
        description=payload.description,
        owner_email=payload.owner_email,
        s3_path=payload.s3_path,
    )
    session.add(dataset)
    try:
        session.flush()  # assign id before audit
        event = _stage_audit(session, actor, "dataset.created", dataset, {"name": dataset.name})
        _commit_and_publish(session, event)
    except IntegrityError:  # lost a create race on the unique name
        session.rollback()
        raise DuplicateDatasetError(payload.name) from None
    session.refresh(dataset)
    return dataset


def update_dataset(
    session: Session, dataset_id: uuid.UUID, payload: DatasetUpdate, actor: str
) -> Dataset:
    dataset = get_dataset(session, dataset_id)
    changes = payload.model_dump(exclude_unset=True)
    for field, value in changes.items():
        setattr(dataset, field, value)
    event = _stage_audit(session, actor, "dataset.updated", dataset, {"changes": sorted(changes)})
    _commit_and_publish(session, event)
    session.refresh(dataset)
    return dataset


def delete_dataset(session: Session, dataset_id: uuid.UUID, actor: str) -> None:
    dataset = get_dataset(session, dataset_id)
    event = _stage_audit(session, actor, "dataset.deleted", dataset, {"name": dataset.name})
    session.delete(dataset)
    _commit_and_publish(session, event)
    # NOTE: deletes catalog metadata only — never the underlying Delta table.


# --- Dataset versions ----------------------------------------------------------


def list_dataset_versions(session: Session, dataset_id: uuid.UUID) -> list[DatasetVersion]:
    get_dataset(session, dataset_id)  # 404 before returning an empty list
    rows = session.scalars(
        select(DatasetVersion)
        .where(DatasetVersion.dataset_id == dataset_id)
        .order_by(DatasetVersion.version.desc())
    ).all()
    return list(rows)


def add_dataset_version(
    session: Session, dataset_id: uuid.UUID, payload: DatasetVersionCreate, actor: str
) -> DatasetVersion:
    """Register the next schema version (monotonic, gap-free per dataset)."""
    dataset = get_dataset(session, dataset_id)
    current = (
        session.scalar(
            select(func.max(DatasetVersion.version)).where(DatasetVersion.dataset_id == dataset_id)
        )
        or 0
    )
    version = DatasetVersion(
        dataset_id=dataset_id,
        version=current + 1,
        schema_json=payload.schema_json,
        row_count=payload.row_count,
    )
    session.add(version)
    try:
        session.flush()
        event = _stage_audit(
            session,
            actor,
            "dataset.version_added",
            dataset,
            {"version": version.version, "row_count": payload.row_count},
        )
        _commit_and_publish(session, event)
    except IntegrityError:  # two pipelines registered simultaneously
        session.rollback()
        raise ConcurrentUpdateError(
            f"version {current + 1} of dataset {dataset_id} was registered concurrently; retry"
        ) from None
    session.refresh(version)
    return version


# --- Audit trail ----------------------------------------------------------------


def list_audit_events(
    session: Session,
    entity_type: str | None = None,
    entity_id: str | None = None,
    actor: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> tuple[list[AuditEvent], int]:
    query = select(AuditEvent)
    if entity_type:
        query = query.where(AuditEvent.entity_type == entity_type)
    if entity_id:
        query = query.where(AuditEvent.entity_id == entity_id)
    if actor:
        query = query.where(AuditEvent.actor == actor)
    total = session.scalar(select(func.count()).select_from(query.subquery())) or 0
    rows = session.scalars(
        query.order_by(AuditEvent.occurred_at.desc(), AuditEvent.id.desc())
        .limit(limit)
        .offset(offset)
    ).all()
    return list(rows), total
