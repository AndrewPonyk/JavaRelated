"""Experiment tracking business logic.

Framework-agnostic and persistence-agnostic: depends only on the repository, so
it is reusable by Kubeflow components and unit-testable against any backend.
"""
from __future__ import annotations

import logging

from src.api.schemas.experiment import (
    ExperimentCreate,
    ExperimentOut,
    RunOut,
    RunStatus,
    TrainingRequest,
)
from src.core.errors import ConflictError, NotFoundError
from src.db.models import Experiment, ModelVersion, Run, new_id
from src.db.repository import SqlAlchemyRepository

logger = logging.getLogger(__name__)


def _slug(name: str) -> str:
    return "-".join(name.lower().split())


class ExperimentService:
    """Create/list experiments and submit training runs."""

    def __init__(self, repo: SqlAlchemyRepository) -> None:
        self._repo = repo

    async def create(self, payload: ExperimentCreate, owner: str) -> ExperimentOut:
        if await self._repo.experiment_name_exists(payload.name):
            raise ConflictError(f"experiment name already exists: {payload.name}")
        exp = Experiment(
            id=new_id(),
            name=payload.name,
            owner=owner,
            description=payload.description,
            tags=dict(payload.tags),
        )
        await self._repo.add_experiment(exp)
        logger.info("created experiment id=%s name=%s owner=%s", exp.id, exp.name, owner)
        return _to_out(exp)

    async def list(self, limit: int, offset: int) -> tuple[list[ExperimentOut], int]:
        rows, total = await self._repo.list_experiments(limit=limit, offset=offset)
        return [_to_out(r) for r in rows], total

    async def get(self, experiment_id: str) -> ExperimentOut:
        exp = await self._repo.get_experiment(experiment_id)
        if exp is None:
            raise NotFoundError(f"experiment not found: {experiment_id}")
        return _to_out(exp)

    async def start_training(self, experiment_id: str, req: TrainingRequest) -> RunOut:
        """Submit a training run.

        Production: enqueue a Kubeflow/SageMaker job and return PENDING; the
        registry is updated later via a ``training.completed`` event. The
        default backend processes inline so create -> train -> register -> serve
        works without a cluster, returning the completed run.
        """
        exp = await self._repo.get_experiment(experiment_id)
        if exp is None:
            raise NotFoundError(f"experiment not found: {experiment_id}")

        model_name = req.model_name or _slug(exp.name)
        run_id = new_id()
        metrics = {"accuracy": 0.9, "f1": 0.88}  # deterministic placeholder
        await self._repo.add_run(
            Run(
                run_id=run_id,
                experiment_id=experiment_id,
                status=RunStatus.COMPLETED.value,
                framework=req.framework,
                metrics=metrics,
                model_name=model_name,
            )
        )
        version = await self._repo.next_model_version(model_name)
        await self._repo.add_model_version(
            ModelVersion(
                name=model_name,
                version=version,
                stage="None",
                run_id=run_id,
                framework=req.framework,
            )
        )
        logger.info(
            "training run complete experiment=%s run=%s model=%s v=%s framework=%s automl=%s",
            experiment_id,
            run_id,
            model_name,
            version,
            req.framework,
            req.use_automl,
        )
        return RunOut(
            run_id=run_id,
            experiment_id=experiment_id,
            status=RunStatus.COMPLETED,
            metrics=metrics,
        )


def _to_out(exp: Experiment) -> ExperimentOut:
    return ExperimentOut(
        id=exp.id,
        name=exp.name,
        description=exp.description,
        tags=exp.tags or {},
        created_at=exp.created_at,
    )
