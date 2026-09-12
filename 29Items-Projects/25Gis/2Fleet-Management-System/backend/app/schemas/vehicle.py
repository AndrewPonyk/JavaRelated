from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class VehicleBase(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    license_plate: str = Field(min_length=2, max_length=32)
    status: str = Field(default="idle", max_length=32)
    driver_id: str | None = None


class VehicleCreate(VehicleBase):
    pass


class VehicleUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=120)
    license_plate: str | None = Field(default=None, min_length=2, max_length=32)
    status: str | None = Field(default=None, max_length=32)
    driver_id: str | None = None
    latest_latitude: float | None = Field(default=None, ge=-90, le=90)
    latest_longitude: float | None = Field(default=None, ge=-180, le=180)
    latest_speed_kph: float | None = Field(default=None, ge=0)
    latest_heading_degrees: float | None = Field(default=None, ge=0, lt=360)


class VehicleRead(VehicleBase):
    model_config = ConfigDict(from_attributes=True)

    id: str
    latest_latitude: float | None = None
    latest_longitude: float | None = None
    latest_speed_kph: float | None = None
    latest_heading_degrees: float | None = None
    last_seen_at: datetime | None = None
    created_at: datetime
    updated_at: datetime
