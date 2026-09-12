"""In-process background job execution for long-running vol-surface fits
(ARCHITECTURE.md §2.2: job table + background worker).

The runner is deliberately in-process: one producer, one consumer, one
process. The seam (submit a coroutine keyed by a job row) is exactly what a
move to dedicated GPU workers would replace — the API contract would not
change (see docs/adr/0001).
"""

from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone

import pandas as pd

from app.config import Settings
from app.db.models import FitJob, VolSurface
from app.db.session import get_session_factory
from quantfinlib.ml.vol_surface import VolSurfaceFitter, VolSurfaceModel
from quantfinlib.utils.validation import QuantFinError

log = logging.getLogger("quantfinlib.api.jobs")


class JobRunner:
    """Tracks background asyncio tasks so shutdown can drain them."""

    def __init__(self) -> None:
        self._tasks: set[asyncio.Task] = set()

    def submit(self, coro) -> asyncio.Task:
        task = asyncio.create_task(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    @property
    def active_count(self) -> int:
        return len(self._tasks)

    async def shutdown(self, timeout: float = 30.0) -> None:
        """Drain in-flight jobs; cancel whatever exceeds the grace period."""
        if not self._tasks:
            return
        pending = set(self._tasks)
        log.info("draining %d background job(s)", len(pending))
        _, still_pending = await asyncio.wait(pending, timeout=timeout)
        for task in still_pending:
            task.cancel()
        if still_pending:
            await asyncio.gather(*still_pending, return_exceptions=True)


def _do_fit(fit_params: dict, settings: Settings) -> VolSurfaceModel:
    """CPU-bound training; runs in a worker thread via asyncio.to_thread."""
    quotes = pd.DataFrame(fit_params["quotes"])
    fitter = VolSurfaceFitter(
        epochs=fit_params["epochs"],
        seed=fit_params["seed"],
        device=settings.ml_device,
        max_seconds=float(settings.ml_max_fit_seconds),
    )
    return fitter.fit(quotes, forward=fit_params["forward"])


async def run_fit_job(job_id: str, fit_params: dict, settings: Settings) -> None:
    factory = get_session_factory()

    async with factory() as session:
        job = await session.get(FitJob, job_id)
        if job is None:  # row vanished (test teardown, manual delete): nothing to do
            return
        job.status = "RUNNING"
        await session.commit()

    try:
        model = await asyncio.to_thread(_do_fit, fit_params, settings)
        blob = model.to_bytes()
        async with factory() as session:
            job = await session.get(FitJob, job_id)
            if job is None:  # row vanished mid-fit: the surface has no owner, drop it
                log.warning("fit job %s finished but its row is gone; discarding result", job_id)
                return
            session.add(
                VolSurface(
                    job_id=job_id,
                    forward=model.forward,
                    model_blob=blob,
                    mse=model.metrics["mse"],
                    n_quotes=int(model.metrics["n_quotes"]),
                )
            )
            job.status = "DONE"
            job.finished_at = datetime.now(timezone.utc)
            await session.commit()
        log.info("fit job %s done (mse=%.3g)", job_id, model.metrics["mse"])
    except QuantFinError as exc:  # expected numerics/validation failures
        await _mark_failed(job_id, str(exc))
    except asyncio.CancelledError:
        await _mark_failed(job_id, "cancelled during shutdown")
        raise
    except Exception:
        log.exception("fit job %s crashed", job_id)
        await _mark_failed(job_id, "internal error (see server logs)")


async def _mark_failed(job_id: str, detail: str) -> None:
    async with get_session_factory()() as session:
        job = await session.get(FitJob, job_id)
        if job is not None:
            job.status = "FAILED"
            job.detail = detail
            job.finished_at = datetime.now(timezone.utc)
            await session.commit()
