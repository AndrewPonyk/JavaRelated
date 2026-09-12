"""Shared response schemas."""

from __future__ import annotations

from pydantic import BaseModel, Field


class ProblemDetail(BaseModel):
    """RFC 7807 problem document returned for every error response."""

    type: str = Field(default="about:blank", description="Problem type URI.")
    title: str = Field(description="Short human-readable summary.")
    status: int = Field(description="HTTP status code.")
    detail: str | None = Field(default=None, description="Occurrence-specific explanation.")
