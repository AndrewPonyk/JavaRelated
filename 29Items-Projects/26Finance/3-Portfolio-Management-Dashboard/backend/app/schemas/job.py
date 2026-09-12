from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict


class JobAccepted(BaseModel):
    """Returned (202) when heavy work is dispatched to a worker."""

    job_id: str
    status: str = "queued"


class JobStatus(BaseModel):
    """Full job record returned by GET /jobs/{id}."""

    model_config = ConfigDict(from_attributes=True)

    id: str
    portfolio_id: int
    status: str  # queued | running | done | failed
    job_type: str
    result: dict | None = None
    error: str | None = None
    created_at: datetime
    updated_at: datetime
