"""Celery task for the asynchronous compute plane.

Terminal-status guarantee (docs/ARCHITECTURE.md §2.6): every claimed
computation ends `succeeded` or `failed` with a machine-readable error_code —
jobs never vanish. Idempotent: re-delivery of an already-terminal computation
is a no-op.

Isolation: the actual math runs in the sciengine sandbox subprocess (killable,
budgeted). Celery's own soft/hard limits are the outer backstop.
"""

from __future__ import annotations

import logging
import uuid
from datetime import UTC, datetime

from app.db.models import Computation
from app.db.sync_session import sync_session
from app.workers.celery_app import celery_app
from app.workers.executors import execute_computation
from sciengine.exceptions import SciEngineError

logger = logging.getLogger(__name__)


@celery_app.task(name="compute.run_computation", bind=True, acks_late=True)
def run_computation(self, computation_id: str) -> str:
    with sync_session() as session:
        computation = session.get(Computation, uuid.UUID(computation_id))
        if computation is None:
            logger.warning("computation %s not found; dropping task", computation_id)
            return "missing"
        if computation.status in ("succeeded", "failed"):
            return computation.status  # idempotency: re-delivery of terminal work

        computation.status = "running"
        session.commit()

        try:
            result = execute_computation(
                computation.kind, dict(computation.input_payload), str(computation.id)
            )
            computation.status = "succeeded"
            computation.result_payload = result
            computation.error_code = None
        except SciEngineError as exc:
            computation.status = "failed"
            computation.error_code = exc.code
            computation.result_payload = {"error": str(exc)}
            logger.warning("computation %s failed: %s (%s)", computation_id, exc, exc.code)
        except Exception as exc:  # - terminal-status guarantee
            computation.status = "failed"
            computation.error_code = "internal_error"
            computation.result_payload = {"error": "Internal error while computing."}
            logger.error("computation %s crashed: %s", computation_id, exc, exc_info=True)

        computation.completed_at = datetime.now(UTC)
        session.commit()
        return computation.status
