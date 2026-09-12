"""Async job orchestration for heavy compute (Monte Carlo).

Implements the ARCHITECTURE async flow: the API creates a ``SimulationJob`` row
(status ``queued``) and dispatches it; a Celery worker (or, in eager mode, an
inline call) computes the result and persists it back to the job row; clients
poll ``GET /api/v1/jobs/{id}``.
"""

from __future__ import annotations

import logging

from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.exceptions import NotFoundError
from app.models.job import SimulationJob
from app.models.portfolio import Portfolio
from app.models.user import User
from app.schemas.risk import MonteCarloRequest
from app.services import portfolio_service

logger = logging.getLogger("app.jobs")


def dispatch_monte_carlo(
    db: Session, portfolio: Portfolio, user: User, req: MonteCarloRequest
) -> SimulationJob:
    """Create a queued job and dispatch it (inline when eager, else to Celery)."""
    settings = get_settings()
    job = SimulationJob(
        portfolio_id=portfolio.id,
        owner_id=user.id,
        status="queued",
        job_type="monte_carlo",
        params=req.model_dump(),
    )
    db.add(job)
    db.commit()
    db.refresh(job)
    job_id = job.id

    if settings.celery_task_always_eager:
        run_simulation_job(job_id)  # runs in its own session, persists result
        db.refresh(job)
    else:
        from app.workers.tasks import simulate_job_task

        simulate_job_task.delay(job_id)
    return job


def get_job(db: Session, job_id: str, user: User) -> SimulationJob:
    job = db.get(SimulationJob, job_id)
    if job is None or job.owner_id != user.id:
        raise NotFoundError(f"Job {job_id} not found")
    return job


def run_simulation_job(job_id: str) -> None:
    """Execute a simulation job in its own DB session (worker or inline)."""
    from app.db.session import SessionLocal

    settings = get_settings()
    db = SessionLocal()
    try:
        job = db.get(SimulationJob, job_id)
        if job is None:
            logger.error("Simulation job %s not found", job_id)
            return
        job.status = "running"
        db.commit()

        portfolio = db.get(Portfolio, job.portfolio_id)
        if portfolio is None:
            job.status = "failed"
            job.error = "Portfolio not found"
            db.commit()
            return

        req = MonteCarloRequest(**(job.params or {}))
        result = portfolio_service.run_monte_carlo(db, portfolio, req, settings)
        job.result = result.model_dump()
        job.status = "done"
        job.error = None
        db.commit()
    except Exception as exc:  # noqa: BLE001 - record failure on the job
        logger.exception("Simulation job %s failed", job_id)
        db.rollback()
        failed = db.get(SimulationJob, job_id)
        if failed is not None:
            failed.status = "failed"
            failed.error = str(exc)[:1024]
            db.commit()
    finally:
        db.close()
