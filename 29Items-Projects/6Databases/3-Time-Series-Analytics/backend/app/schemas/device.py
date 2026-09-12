"""Device registry models."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field


class DeviceCreate(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    site: str = Field(default="default", max_length=64)
    device_type: str = Field(default="generic", max_length=64)


class Device(DeviceCreate):
    device_id: str
    enabled: bool = True
    created_at: datetime | None = None


class DeviceWithKey(Device):
    """Returned exactly once, at registration — the key is stored only as a hash."""

    api_key: str
