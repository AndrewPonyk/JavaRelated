"""Volatility-surface endpoints — the long-running-job pattern.

POST /fit returns 202 + job id immediately; the fit runs in the background
(services/job_runner.py). Clients poll /jobs/{id}, then query the fitted
surface by the surface id in result_url.
"""

from __future__ import annotations

import threading
from collections import OrderedDict

from fastapi import APIRouter, HTTPException, Query, Request, status
from pydantic import BaseModel, Field
from sqlalchemy import select

from app.db.models import FitJob, VolSurface
from app.dependencies import Db, FitCaller, SettingsDep
from app.schemas.common import JobStatus
from quantfinlib.ml.vol_surface import VolSurfaceModel

router = APIRouter()

# Deserialized surfaces are immutable; keep the hot ones in memory so
# repeated σ(K,T) queries don't re-load the blob on every request.
_MODEL_CACHE_MAX = 16
_model_cache: OrderedDict[str, VolSurfaceModel] = OrderedDict()
_model_cache_lock = threading.Lock()


def _cached_model(surface_id: str, blob: bytes) -> VolSurfaceModel:
    with _model_cache_lock:
        model = _model_cache.get(surface_id)
        if model is not None:
            _model_cache.move_to_end(surface_id)
            return model
    model = VolSurfaceModel.from_bytes(blob)
    with _model_cache_lock:
        _model_cache[surface_id] = model
        while len(_model_cache) > _MODEL_CACHE_MAX:
            _model_cache.popitem(last=False)
    return model


class Quote(BaseModel):
    strike: float = Field(gt=0)
    expiry: float = Field(gt=0, le=100)
    implied_vol: float = Field(gt=0, le=5.0)


class FitRequest(BaseModel):
    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "forward": 100.0,
                    "quotes": [
                        {"strike": 90 + 2 * i, "expiry": 1.0, "implied_vol": 0.2 + 0.001 * i}
                        for i in range(10)
                    ],
                    "epochs": 2000,
                    "seed": 42,
                }
            ]
        }
    }

    forward: float = Field(gt=0, description="Forward price the surface is relative to")
    quotes: list[Quote] = Field(min_length=10, max_length=50_000)
    epochs: int = Field(default=2000, gt=0, le=100_000)
    seed: int = 42


class SurfaceQueryResponse(BaseModel):
    surface_id: str
    strike: float
    expiry: float
    implied_vol: float
    request_id: str


class SurfaceInfoResponse(BaseModel):
    surface_id: str
    forward: float
    mse: float
    n_quotes: int
    created_at: str


def _job_status(job: FitJob, surface_id: str | None) -> JobStatus:
    return JobStatus(
        job_id=job.id,
        status=job.status,  # type: ignore[arg-type]
        created_at=job.created_at,
        detail=job.detail,
        result_url=f"/v1/vol-surface/{surface_id}" if surface_id else None,
    )


@router.post("/fit", response_model=JobStatus, status_code=status.HTTP_202_ACCEPTED)
async def fit_surface(
    body: FitRequest, request: Request, caller: FitCaller, db: Db, settings: SettingsDep
) -> JobStatus:
    """Submit a surface-fitting job. Returns 202 + job id; poll
    /v1/vol-surface/jobs/{id} until DONE, then follow result_url."""
    from app.services.job_runner import run_fit_job

    job = FitJob(caller=caller)
    db.add(job)
    await db.commit()

    fit_params = {
        "forward": body.forward,
        "quotes": [q.model_dump() for q in body.quotes],
        "epochs": body.epochs,
        "seed": body.seed,
    }
    request.app.state.job_runner.submit(run_fit_job(job.id, fit_params, settings))
    return _job_status(job, None)


@router.get("/jobs/{job_id}", response_model=JobStatus)
async def job_status(job_id: str, caller: FitCaller, db: Db) -> JobStatus:
    job = await db.get(FitJob, job_id)
    if job is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"unknown job {job_id!r}")
    surface_id: str | None = None
    if job.status == "DONE":
        row = await db.execute(select(VolSurface.id).where(VolSurface.job_id == job_id))
        surface_id = row.scalar_one_or_none()
    return _job_status(job, surface_id)


@router.get("/{surface_id}/info", response_model=SurfaceInfoResponse)
async def surface_info(surface_id: str, caller: FitCaller, db: Db) -> SurfaceInfoResponse:
    surface = await db.get(VolSurface, surface_id)
    if surface is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"unknown surface {surface_id!r}")
    return SurfaceInfoResponse(
        surface_id=surface.id,
        forward=surface.forward,
        mse=surface.mse,
        n_quotes=surface.n_quotes,
        created_at=surface.created_at.isoformat(),
    )


@router.get("/{surface_id}", response_model=SurfaceQueryResponse)
async def query_surface(
    surface_id: str,
    request: Request,
    caller: FitCaller,
    db: Db,
    strike: float = Query(gt=0),
    expiry: float = Query(gt=0, le=100),
) -> SurfaceQueryResponse:
    """Evaluate a fitted surface: σ(strike, expiry)."""
    surface = await db.get(VolSurface, surface_id)
    if surface is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"unknown surface {surface_id!r}")
    model = _cached_model(surface.id, surface.model_blob)
    implied_vol = float(model.vol(strike, expiry))
    return SurfaceQueryResponse(
        surface_id=surface.id,
        strike=strike,
        expiry=expiry,
        implied_vol=implied_vol,
        request_id=request.state.request_id,
    )
