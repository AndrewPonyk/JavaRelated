"""Repositories encapsulating persistence for the canonical tables."""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from fraud_detection.db.models import ABAssignment, DriftReport, ModelVersion, Prediction

# Module-level aliases keep annotations unambiguous inside classes that
# define a ``list`` method (which shadows the builtin in class scope).
Predictions = list[Prediction]
PredictionPage = tuple[Predictions, int]
ModelVersions = list[ModelVersion]
DriftReports = list[DriftReport]


class PredictionRepository:
    """Persistence for prediction audit records."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, prediction: Prediction) -> Prediction:
        """Persist one prediction record."""
        self._session.add(prediction)
        self._session.flush()
        return prediction

    def get_by_transaction_id(self, transaction_id: str) -> Prediction | None:
        """Return the prediction for a transaction id, if recorded."""
        stmt = select(Prediction).where(Prediction.transaction_id == transaction_id)
        return self._session.scalars(stmt).first()

    def list(
        self, limit: int = 50, offset: int = 0, account_id: str | None = None
    ) -> PredictionPage:
        """Return a page of predictions (newest first) and the total count."""
        stmt = select(Prediction)
        count_stmt = select(func.count()).select_from(Prediction)
        if account_id is not None:
            stmt = stmt.where(Prediction.account_id == account_id)
            count_stmt = count_stmt.where(Prediction.account_id == account_id)
        stmt = stmt.order_by(Prediction.created_at.desc()).limit(limit).offset(offset)
        items = list(self._session.scalars(stmt).all())
        total = int(self._session.scalar(count_stmt) or 0)
        return items, total

    def list_recent(self, limit: int) -> Predictions:
        """Return the most recent predictions (newest first), for drift checks."""
        stmt = select(Prediction).order_by(Prediction.created_at.desc()).limit(limit)
        return list(self._session.scalars(stmt).all())


class ModelVersionRepository:
    """Persistence for the model version catalog."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, model_version: ModelVersion) -> ModelVersion:
        """Persist one model version record."""
        self._session.add(model_version)
        self._session.flush()
        return model_version

    def get_by_version(self, version: str) -> ModelVersion | None:
        """Return the record for a version string, if present."""
        stmt = select(ModelVersion).where(ModelVersion.version == version)
        return self._session.scalars(stmt).first()

    def list(self) -> ModelVersions:
        """Return all known model versions, newest registration first."""
        stmt = select(ModelVersion).order_by(ModelVersion.registered_at.desc())
        return list(self._session.scalars(stmt).all())

    def update_stage(self, version: str, stage: str) -> ModelVersion | None:
        """Set the stage/alias for a version; returns the updated row or None."""
        record = self.get_by_version(version)
        if record is not None:
            record.stage = stage
            self._session.flush()
        return record

    def delete(self, version: str) -> bool:
        """Delete a version record; returns True when a row was removed."""
        record = self.get_by_version(version)
        if record is None:
            return False
        self._session.delete(record)
        self._session.flush()
        return True


class ABAssignmentRepository:
    """Persistence for sticky A/B assignments."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def get_by_account(self, account_id: str) -> ABAssignment | None:
        """Return the existing assignment for an account, if any."""
        stmt = (
            select(ABAssignment)
            .where(ABAssignment.account_id == account_id)
            .order_by(ABAssignment.assigned_at.desc())
        )
        return self._session.scalars(stmt).first()

    def add(self, assignment: ABAssignment) -> ABAssignment:
        """Persist a new assignment."""
        self._session.add(assignment)
        self._session.flush()
        return assignment


class DriftReportRepository:
    """Persistence for drift check outcomes."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add_many(self, reports: DriftReports) -> DriftReports:
        """Persist a batch of per-feature drift reports."""
        self._session.add_all(reports)
        self._session.flush()
        return reports

    def list(self, limit: int = 50) -> DriftReports:
        """Return the most recent drift reports (newest first)."""
        stmt = select(DriftReport).order_by(DriftReport.created_at.desc()).limit(limit)
        return list(self._session.scalars(stmt).all())
