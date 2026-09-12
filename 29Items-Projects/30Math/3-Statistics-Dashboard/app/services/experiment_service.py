"""Experiment registry: CRUD + guarded lifecycle over the experiments table.

Returns plain dataclasses — the UI never holds ORM objects (ARCHITECTURE §2.2).
All functions require a configured database; the Experiments page explains that
in demo mode instead of calling them.
"""

from __future__ import annotations

from dataclasses import dataclass

from app.core.config import get_settings
from app.core.errors import StorageError
from app.core.logging import get_logger

logger = get_logger(__name__)


@dataclass(frozen=True)
class ExperimentInfo:
    id: str
    name: str
    hypothesis: str | None
    primary_metric: str | None
    status: str
    planned_n_per_variant: int | None
    expected_ratio: float
    created_at: str
    run_count: int


def _require_db() -> None:
    if get_settings().demo_mode:
        raise StorageError(
            "experiments need a database",
            user_message="Configure DATABASE_URL to use the experiment registry.",
        )


def _to_info(experiment, run_count: int) -> ExperimentInfo:  # experiment: session-bound ORM object
    return ExperimentInfo(
        id=experiment.id,
        name=experiment.name,
        hypothesis=experiment.hypothesis,
        primary_metric=experiment.primary_metric,
        status=experiment.status,
        planned_n_per_variant=experiment.planned_n_per_variant,
        expected_ratio=experiment.expected_ratio,
        created_at=experiment.created_at.isoformat(sep=" ", timespec="minutes")
        if experiment.created_at
        else "",
        run_count=run_count,
    )


def create_experiment(
    name: str,
    *,
    hypothesis: str | None = None,
    primary_metric: str | None = None,
    planned_n_per_variant: int | None = None,
    expected_ratio: float = 0.5,
) -> str:
    _require_db()
    from app.data.db import session_scope
    from app.data.models import Experiment
    from app.data.repositories import ExperimentRepository

    with session_scope() as session:
        experiment = ExperimentRepository(session).add(
            Experiment(
                name=name.strip(),
                hypothesis=hypothesis,
                primary_metric=primary_metric,
                planned_n_per_variant=planned_n_per_variant,
                expected_ratio=expected_ratio,
            )
        )
        experiment_id = experiment.id
    logger.info("Experiment created: %s (%s)", name, experiment_id)
    return experiment_id


def list_experiments() -> list[ExperimentInfo]:
    if get_settings().demo_mode:
        return []
    from app.data.db import session_scope
    from app.data.repositories import ExperimentRepository

    with session_scope() as session:
        # Counts come from a single aggregated query — no per-experiment lazy loads.
        return [
            _to_info(experiment, count)
            for experiment, count in ExperimentRepository(session).list_all_with_run_counts()
        ]


def get_experiment(experiment_id: str) -> ExperimentInfo | None:
    if get_settings().demo_mode:
        return None
    from app.data.db import session_scope
    from app.data.repositories import AnalysisRunRepository, ExperimentRepository

    with session_scope() as session:
        experiment = ExperimentRepository(session).get(experiment_id)
        if experiment is None:
            return None
        run_count = AnalysisRunRepository(session).count_for_experiment(experiment_id)
        return _to_info(experiment, run_count)


def advance_status(experiment_id: str, new_status: str) -> ExperimentInfo:
    """Forward-only lifecycle: draft → running → completed (guarded in the repository)."""
    _require_db()
    from app.data.db import session_scope
    from app.data.repositories import AnalysisRunRepository, ExperimentRepository

    with session_scope() as session:
        experiment = ExperimentRepository(session).set_status(experiment_id, new_status)
        run_count = AnalysisRunRepository(session).count_for_experiment(experiment_id)
        info = _to_info(experiment, run_count)
    logger.info("Experiment %s → %s", experiment_id, new_status)
    return info


def delete_experiment(experiment_id: str) -> bool:
    _require_db()
    from app.data.db import session_scope
    from app.data.repositories import ExperimentRepository

    with session_scope() as session:
        deleted = ExperimentRepository(session).delete(experiment_id)
    if deleted:
        logger.info("Experiment deleted: %s", experiment_id)
    return deleted
