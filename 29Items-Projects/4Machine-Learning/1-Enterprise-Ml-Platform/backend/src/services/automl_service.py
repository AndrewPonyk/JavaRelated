"""AutoML service — TPOT-driven feature engineering and model search.

Submits AutoML jobs to the batch plane (Kubeflow) and registers the best
discovered pipeline as a candidate model version.
"""
from __future__ import annotations

import logging
import uuid

logger = logging.getLogger(__name__)


class AutoMLService:
    """Drive TPOT-based feature engineering + pipeline search."""

    async def submit_search(
        self,
        experiment_id: str,
        dataset_uri: str,
        generations: int = 5,
        population_size: int = 50,
        max_time_mins: int = 60,
    ) -> str:
        """Kick off an AutoML search job; return a job handle.

        TODO: launch a Kubeflow component wrapping
        ``tpot.TPOTClassifier/Regressor`` with the given budget, log the search
        to MLflow, and export the fitted sklearn pipeline as an artifact.
        """
        job_id = str(uuid.uuid4())
        logger.info(
            "automl submit exp=%s dataset=%s gens=%s pop=%s budget=%smin job=%s",
            experiment_id,
            dataset_uri,
            generations,
            population_size,
            max_time_mins,
            job_id,
        )
        return job_id

    async def register_best(self, job_id: str, model_name: str) -> int:
        """Register the best pipeline from a finished search as a new version.

        TODO: locate the winning MLflow run for ``job_id`` and register it.
        """
        logger.info("registering best automl pipeline job=%s as %s", job_id, model_name)
        return 1
