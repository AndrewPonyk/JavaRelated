"""Recommendation Celery tasks (routed to the `ml` queue)."""

from __future__ import annotations

import logging

from celery import shared_task

logger = logging.getLogger(__name__)


@shared_task(ignore_result=True)
def train_model() -> str:
    """Nightly retrain + refresh of per-user recommendation caches.

    Returns a short status string (handy for beat logs / tests).
    """
    from apps.recommendations.models import Interaction

    from .ml import inference, training

    if not Interaction.objects.exists():
        logger.info("reco.train.skipped", extra={"reason": "no interactions"})
        return "skipped: no interactions"

    logger.info("reco.train.start")
    _model, blob = training.train_from_db()
    training.save_checkpoint(blob)

    # Refresh the cached recommendations for every known user.
    inference._MODEL = None  # force reload of the new checkpoint
    for user_id in blob["user_id_to_index"]:
        inference.precompute_for_user(user_id)

    logger.info("reco.train.done", extra={"users": len(blob["user_id_to_index"])})
    return f"trained: {blob['num_users']} users, {blob['num_items']} items"


@shared_task(ignore_result=True)
def refresh_user_recommendations(user_id: int) -> None:
    from .ml import inference

    inference.precompute_for_user(user_id)
