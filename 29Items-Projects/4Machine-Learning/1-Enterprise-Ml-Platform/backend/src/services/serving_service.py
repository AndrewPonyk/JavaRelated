"""Unified serving layer.

Hides *where* a model runs (in-cluster predictor vs. SageMaker endpoint) and
*which framework* it uses behind a single ``predict`` call. Resolves the A/B
variant to a concrete registered version, then loads/serves it.
"""
from __future__ import annotations

import logging

from src.api.schemas.model import PredictionResponse
from src.core.errors import NotFoundError
from src.db.repository import SqlAlchemyRepository
from src.ml.serving.predictor import Predictor, load_predictor

logger = logging.getLogger(__name__)

# Warm predictor pool keyed by (model, version). Module-level so it survives
# across requests even though services are constructed per-request.
_POOL: dict[tuple[str, int], Predictor] = {}


class ServingService:
    """Resolve a predictor and run inference for a model variant."""

    def __init__(self, repo: SqlAlchemyRepository) -> None:
        self._repo = repo

    async def predict(
        self, model_name: str, variant: str, features: dict[str, object]
    ) -> PredictionResponse:
        version = await self._resolve_version(model_name, variant)
        predictor = _get_predictor(model_name, version)
        try:
            raw = predictor.predict(features)
        except NotImplementedError:
            # A backend (e.g. SageMaker) is configured but not wired — surface a
            # clear 404 rather than a 500.
            logger.error("predictor unavailable model=%s v=%s", model_name, version)
            raise NotFoundError(f"no servable predictor for {model_name} v{version}")
        return PredictionResponse(
            prediction=raw,
            model_version=version,
            variant=variant,
            confidence=raw if isinstance(raw, float) else None,
        )

    async def _resolve_version(self, model_name: str, variant: str) -> int:
        if variant == "challenger":
            test = await self._repo.active_ab_test(model_name)
            if test is not None:
                return test.challenger_version
        rec = await self._repo.production_version(model_name)
        if rec is None:
            raise NotFoundError(f"no registered version for model: {model_name}")
        return rec.version


def _get_predictor(model_name: str, version: int) -> Predictor:
    key = (model_name, version)
    if key not in _POOL:
        logger.info("cold-loading predictor %s v%s", model_name, version)
        _POOL[key] = load_predictor(model_name, version)
    return _POOL[key]
