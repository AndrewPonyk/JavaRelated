"""Series API schemas."""

from __future__ import annotations

import uuid

from pydantic import BaseModel, ConfigDict


class SeriesRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    series_instance_uid: str
    modality: str | None = None
    series_number: int | None = None
    description: str | None = None
    body_part: str | None = None
    instance_count: int = 0
