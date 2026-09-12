"""Model registry business logic.

Stage transitions are the *only* promotion mechanism — model weights are never
baked into images. Production swaps this repository for the MLflow Model Registry.
"""
from __future__ import annotations

import logging

from src.api.schemas.model import ModelStage, ModelVersionOut, StageTransitionRequest
from src.core.errors import NotFoundError
from src.db.models import ModelVersion
from src.db.repository import SqlAlchemyRepository

logger = logging.getLogger(__name__)


class ModelRegistryService:
    """List model versions and manage stage transitions."""

    def __init__(self, repo: SqlAlchemyRepository) -> None:
        self._repo = repo

    async def list_versions(self, name: str) -> list[ModelVersionOut]:
        return [_to_out(m) for m in await self._repo.list_model_versions(name)]

    async def transition_stage(
        self, name: str, version: int, req: StageTransitionRequest
    ) -> ModelVersionOut:
        """Transition a model version to a new stage.

        Promoting to Production optionally archives the prior Production version
        so exactly one is live at a time.
        """
        rec = await self._repo.get_model_version(name, version)
        if rec is None:
            raise NotFoundError(f"model version not found: {name} v{version}")

        if req.stage == ModelStage.PRODUCTION and req.archive_existing:
            current = await self._repo.production_version(name)
            if (
                current is not None
                and current.stage == "Production"
                and current.version != version
            ):
                current.stage = ModelStage.ARCHIVED.value
                logger.info("archived prior production %s v%s", name, current.version)

        rec.stage = req.stage.value
        logger.info("transition model=%s v=%s -> %s", name, version, req.stage.value)
        return _to_out(rec)

    async def get_production_version(self, name: str) -> int:
        """Return the current champion version number for a model."""
        rec = await self._repo.production_version(name)
        if rec is None:
            raise NotFoundError(f"no servable version for model: {name}")
        return rec.version


def _to_out(rec: ModelVersion) -> ModelVersionOut:
    return ModelVersionOut(
        name=rec.name,
        version=rec.version,
        stage=ModelStage(rec.stage),
        run_id=rec.run_id,
        framework=rec.framework,
    )
