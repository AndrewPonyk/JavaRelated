"""Scheduled trial-monitoring tasks (Celery Beat).

``detect_protocol_deviations`` scans active subjects for visits that have fallen
outside their protocol-defined window and records a deviation. See
ARCHITECTURE.md Flow B. The detection logic lives in the enrollment service;
this task is the scheduled entrypoint.
"""
from __future__ import annotations

import logging

from celery import shared_task

logger = logging.getLogger(__name__)


@shared_task(
    name="apps.trials.tasks.detect_protocol_deviations",
    bind=True,
    max_retries=3,
    default_retry_delay=60,
    acks_late=True,
)
def detect_protocol_deviations(self) -> dict:
    """Idempotent: re-running creates no duplicate deviation rows."""
    from apps.enrollment.services import detect_deviations

    created = detect_deviations()
    logger.info("Protocol deviation scan complete; created=%s", created)
    return {"deviations_created": created}
