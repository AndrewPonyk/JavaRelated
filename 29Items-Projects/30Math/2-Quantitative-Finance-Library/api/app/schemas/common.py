"""Shared response envelopes."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel


class JobStatus(BaseModel):
    job_id: str
    status: Literal["PENDING", "RUNNING", "DONE", "FAILED"]
    created_at: datetime
    detail: str | None = None
    result_url: str | None = None
