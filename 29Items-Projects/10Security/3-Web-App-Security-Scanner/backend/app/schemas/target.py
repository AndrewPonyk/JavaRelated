"""Pydantic schemas for scan-target allowlist management."""

from __future__ import annotations

import ipaddress
import re
from datetime import datetime

from pydantic import BaseModel, Field, field_validator

# hostname (single label ok for internal hosts) — no scheme, no path, no spaces
_HOST_RE = re.compile(r"^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$", re.IGNORECASE)


class TargetCreate(BaseModel):
    host_pattern: str = Field(min_length=3, max_length=255)
    description: str | None = Field(default=None, max_length=2000)

    @field_validator("host_pattern")
    @classmethod
    def _validate_pattern(cls, v: str) -> str:
        v = v.strip().lower().rstrip(".")
        # CIDR / single-IP first — they legitimately contain "/"
        try:
            ipaddress.ip_network(v, strict=False)
            return v
        except ValueError:
            pass
        if "://" in v or "/" in v or " " in v:
            raise ValueError("pattern must be a bare hostname or CIDR — no scheme or path")
        if not _HOST_RE.match(v):
            raise ValueError(
                "pattern must be a valid hostname or CIDR (e.g. app.example.com, 10.0.0.0/8)"
            )
        return v


class TargetRead(BaseModel):
    id: int
    host_pattern: str
    description: str | None = None
    verified_at: datetime | None = None
    created_at: datetime | None = None

    model_config = {"from_attributes": True}
