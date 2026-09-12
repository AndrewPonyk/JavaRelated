"""Pydantic schemas for finding endpoints."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel

from app.models.finding import Severity, Source


class FindingRead(BaseModel):
    id: int
    scan_id: int
    source: Source
    rule_id: str
    title: str
    url: str
    param: str | None = None
    severity: Severity
    severity_confidence: float | None = None
    owasp_category: str | None = None
    cwe_id: str | None = None
    description: str | None = None

    model_config = {"from_attributes": True}


class FindingDetail(FindingRead):
    """Single-finding view: adds method + redacted evidence + timestamps."""

    method: str | None = None
    evidence: dict = {}
    created_at: datetime | None = None


class SeverityStats(BaseModel):
    severity: Severity
    count: int


class FindingsPage(BaseModel):
    items: list[FindingRead]
    total: int
    page: int
    page_size: int
