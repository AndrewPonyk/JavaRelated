from __future__ import annotations

import re
from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field, field_validator

NAME_PATTERN = r"^[a-z][a-z0-9_-]{2,63}$"
CRON_PATTERN = r"^\S+ \S+ \S+ \S+ \S+$"  # shape check; field semantics below

# One cron field: * | N | N-N | mon/jan names, each optionally /step, comma-listed.
_CRON_ATOM = r"(\*|[0-9]{1,2}(-[0-9]{1,2})?|[a-z]{3})(/[0-9]{1,2})?"
_CRON_FIELD = re.compile(rf"^{_CRON_ATOM}(,{_CRON_ATOM})*$", re.IGNORECASE)


def validate_cron(value: str) -> str:
    fields = value.split()
    if len(fields) != 5:
        raise ValueError("schedule must have exactly 5 cron fields (m h dom mon dow)")
    for field in fields:
        if not _CRON_FIELD.match(field):
            raise ValueError(f"invalid cron field: {field!r}")
    return value


class PipelineStatus(str, Enum):
    draft = "draft"
    active = "active"
    paused = "paused"


class PipelineBase(BaseModel):
    name: str = Field(pattern=NAME_PATTERN, examples=["orders-daily"])
    description: str = Field(default="", max_length=500)
    schedule: str = Field(pattern=CRON_PATTERN, examples=["0 2 * * *"])

    @field_validator("schedule")
    @classmethod
    def _schedule_is_valid_cron(cls, value: str) -> str:
        return validate_cron(value)

    source: str = Field(min_length=1, max_length=500, examples=["s3://lake/raw/orders/"])
    target: str = Field(
        min_length=1, max_length=500, examples=["analytics.marts.fct_business_metrics_daily"]
    )
    transform_ref: str = Field(
        default="", max_length=200, description="dbt selector or Glue job name"
    )


class PipelineCreate(PipelineBase):
    pass


class PipelineUpdate(BaseModel):
    """Partial update — only provided fields are applied."""

    name: str | None = Field(default=None, pattern=NAME_PATTERN)
    description: str | None = Field(default=None, max_length=500)
    schedule: str | None = Field(default=None, pattern=CRON_PATTERN)

    @field_validator("schedule")
    @classmethod
    def _schedule_is_valid_cron(cls, value: str | None) -> str | None:
        return None if value is None else validate_cron(value)

    source: str | None = Field(default=None, min_length=1, max_length=500)
    target: str | None = Field(default=None, min_length=1, max_length=500)
    transform_ref: str | None = Field(default=None, max_length=200)
    status: PipelineStatus | None = None


class PipelineRead(PipelineBase):
    id: str
    status: PipelineStatus
    created_at: datetime
    updated_at: datetime
