"""Repositories — the only place queries are written. Sessions are injected by callers."""

from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.orm import Session, joinedload

from app.core.errors import DataValidationError
from app.data.models import AnalysisRun, Dataset, Experiment

#: Allowed experiment lifecycle transitions (forward-only).
_EXPERIMENT_TRANSITIONS: dict[str, set[str]] = {
    "draft": {"running"},
    "running": {"completed"},
    "completed": set(),
}


class DatasetRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, dataset: Dataset) -> Dataset:
        self._session.add(dataset)
        self._session.flush()  # assign the PK before the caller commits
        return dataset

    def get(self, dataset_id: str) -> Dataset | None:
        return self._session.get(Dataset, dataset_id)

    def get_by_content_hash(self, content_hash: str) -> Dataset | None:
        stmt = select(Dataset).where(Dataset.content_hash == content_hash)
        return self._session.scalars(stmt).first()

    def list_recent(
        self, limit: int = 20, offset: int = 0, created_by_email: str | None = None
    ) -> list[Dataset]:
        stmt = select(Dataset).order_by(Dataset.created_at.desc()).limit(limit).offset(offset)
        if created_by_email is not None:
            stmt = stmt.where(Dataset.created_by_email == created_by_email)
        return list(self._session.scalars(stmt))

    def delete(self, dataset_id: str) -> bool:
        """Delete a dataset (runs cascade). Returns False when the id is unknown."""
        dataset = self.get(dataset_id)
        if dataset is None:
            return False
        self._session.delete(dataset)
        self._session.flush()
        return True


class AnalysisRunRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, run: AnalysisRun) -> AnalysisRun:
        self._session.add(run)
        self._session.flush()
        return run

    def list_for_dataset(self, dataset_id: str, limit: int = 50) -> list[AnalysisRun]:
        stmt = (
            select(AnalysisRun)
            .where(AnalysisRun.dataset_id == dataset_id)
            .order_by(AnalysisRun.created_at.desc())
            .limit(limit)
        )
        return list(self._session.scalars(stmt))

    def list_recent(self, limit: int = 10) -> list[AnalysisRun]:
        """Most recent runs with their dataset eagerly loaded (for display off-session)."""
        stmt = (
            select(AnalysisRun)
            .options(joinedload(AnalysisRun.dataset))
            .order_by(AnalysisRun.created_at.desc())
            .limit(limit)
        )
        return list(self._session.scalars(stmt))

    def count_for_experiment(self, experiment_id: str) -> int:
        stmt = (
            select(func.count())
            .select_from(AnalysisRun)
            .where(AnalysisRun.experiment_id == experiment_id)
        )
        return int(self._session.scalar(stmt) or 0)


class ExperimentRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, experiment: Experiment) -> Experiment:
        if self.get_by_name(experiment.name) is not None:
            raise DataValidationError(
                f"experiment name taken: {experiment.name!r}",
                user_message=f"An experiment named '{experiment.name}' already exists.",
            )
        self._session.add(experiment)
        self._session.flush()
        return experiment

    def get(self, experiment_id: str) -> Experiment | None:
        return self._session.get(Experiment, experiment_id)

    def get_by_name(self, name: str) -> Experiment | None:
        return self._session.scalars(select(Experiment).where(Experiment.name == name)).first()

    def list_all(self) -> list[Experiment]:
        return list(
            self._session.scalars(select(Experiment).order_by(Experiment.created_at.desc()))
        )

    def list_all_with_run_counts(self) -> list[tuple[Experiment, int]]:
        """Experiments with their logged-run counts in a single query (no N+1)."""
        stmt = (
            select(Experiment, func.count(AnalysisRun.id))
            .outerjoin(AnalysisRun, AnalysisRun.experiment_id == Experiment.id)
            .group_by(Experiment.id)
            .order_by(Experiment.created_at.desc())
        )
        return [(row[0], int(row[1])) for row in self._session.execute(stmt).all()]

    def set_status(self, experiment_id: str, new_status: str) -> Experiment:
        """Forward-only lifecycle: draft → running → completed."""
        experiment = self.get(experiment_id)
        if experiment is None:
            raise DataValidationError(
                f"unknown experiment: {experiment_id}", user_message="Experiment not found."
            )
        allowed = _EXPERIMENT_TRANSITIONS.get(experiment.status, set())
        if new_status not in allowed:
            raise DataValidationError(
                f"illegal transition {experiment.status} -> {new_status}",
                user_message=(
                    f"An experiment cannot move from '{experiment.status}' to '{new_status}'."
                ),
            )
        experiment.status = new_status
        self._session.flush()
        return experiment

    def delete(self, experiment_id: str) -> bool:
        experiment = self.get(experiment_id)
        if experiment is None:
            return False
        self._session.delete(experiment)  # linked runs keep history via ondelete=SET NULL
        self._session.flush()
        return True
