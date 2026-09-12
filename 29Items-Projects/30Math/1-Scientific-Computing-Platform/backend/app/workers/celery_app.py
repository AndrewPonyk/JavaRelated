"""Celery application for the compute plane.

Run with the THREADS pool — the heavy math executes in the sciengine sandbox
subprocess anyway (GIL irrelevant), and prefork's daemonized children cannot
spawn the sandbox process:

    celery -A app.workers.celery_app worker -Q compute --pool=threads --concurrency=4

Windows dev: the same command works (threads pool needs no fork).
The worker environment MUST set OMP_NUM_THREADS=1 (BLAS oversubscription).
"""

from celery import Celery

from app.core.config import get_settings

settings = get_settings()

celery_app = Celery(
    "scp",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.workers.tasks"],
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],  # never pickle — task args cross a trust boundary
    result_serializer="json",
    task_acks_late=True,  # redeliver if a worker dies mid-solve
    worker_prefetch_multiplier=1,  # long tasks: no hoarding
    task_default_queue="compute",
    # Celery limits are the OUTER backstop; the sciengine sandbox enforces the
    # real per-operation budget (worker_op_timeout_seconds) and kills hung math.
    task_soft_time_limit=max(60, int(settings.worker_op_timeout_seconds) + 30),
    task_time_limit=max(90, int(settings.worker_op_timeout_seconds) + 60),
    result_expires=3600,
    # Publishing must fail fast, not hang the API: bounded retries with short
    # backoff; on final failure the row is marked failed(queue_unavailable).
    task_publish_retry_policy={
        "max_retries": 2,
        "interval_start": 0.2,
        "interval_step": 0.3,
        "interval_max": 1.0,
    },
    broker_connection_timeout=2.0,
    # Tests run tasks inline (no broker); errors are handled inside the task
    # body itself, so eager propagation stays off.
    task_always_eager=settings.celery_task_always_eager,
    task_eager_propagates=False,
)
