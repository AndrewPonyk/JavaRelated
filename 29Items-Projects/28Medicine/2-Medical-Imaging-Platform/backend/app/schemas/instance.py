"""Instance API schemas."""

from __future__ import annotations

import uuid

from pydantic import BaseModel, ConfigDict


class InstanceRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    sop_instance_uid: str
    instance_number: int | None = None
    rows: int | None = None
    columns: int | None = None
    transfer_syntax_uid: str | None = None


class InstanceFrameUrl(BaseModel):
    """Presigned, time-boxed URL for direct frame retrieval from object store."""

    sop_instance_uid: str
    url: str
    expires_in_seconds: int
