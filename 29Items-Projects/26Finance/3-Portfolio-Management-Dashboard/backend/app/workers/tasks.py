"""Celery tasks for the asynchronous compute tier.

Heavy, bursty work (large Monte Carlo runs) is dispatched here so it never blocks
the API event loop. Workers scale independently of the API based on Redis queue
depth. The actual computation lives in ``job_service`` (no Celery dependency), so
it is equally runnable inline (eager mode / tests) or from a worker.

Run a worker:  ``celery -A app.workers.tasks.celery_app worker --loglevel=info``
"""

from __future__ import annotations

from celery import Celery

from app.core.config import get_settings

settings = get_settings()

celery_app = Celery(
    "portfolio",
    broker=settings.celery_broker_url,
    backend=settings.celery_result_backend,
)
celery_app.conf.update(
    task_track_started=True,
    result_expires=3600,
    task_always_eager=settings.celery_task_always_eager,
    task_eager_propagates=True,
)


@celery_app.task(name="simulate_job")
def simulate_job_task(job_id: str) -> str:
    """Run a persisted Monte Carlo job by id."""
    from app.services.job_service import run_simulation_job

    run_simulation_job(job_id)
    return job_id
