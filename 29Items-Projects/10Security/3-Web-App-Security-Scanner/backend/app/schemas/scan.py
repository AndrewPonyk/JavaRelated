"""Pydantic schemas for scan endpoints (request/response contracts)."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field, HttpUrl, field_validator

from app.models.scan import ScanProfile, ScanStatus


class ScanCreate(BaseModel):
    target_url: HttpUrl
    profile: ScanProfile = ScanProfile.STANDARD

    @field_validator("target_url")
    @classmethod
    def reject_embedded_credentials(cls, v: HttpUrl) -> HttpUrl:
        if v.username or v.password:
            raise ValueError("URL must not contain embedded credentials")
        return v


class ScanRead(BaseModel):
    id: int
    target_url: str
    profile: ScanProfile
    status: ScanStatus
    started_at: datetime | None = None
    finished_at: datetime | None = None
    error_detail: str | None = None

    model_config = {"from_attributes": True}


class ScanProgress(BaseModel):
    """Coarse progress across the pipeline (queued→spider→sqlmap→xss→done)."""

    scan_id: int
    phase: str = Field(pattern="^(queued|spider|active_scan|sqlmap|xss|ingestion|done|failed)$")
    percent: int = Field(ge=0, le=100)
    findings_so_far: int = 0
