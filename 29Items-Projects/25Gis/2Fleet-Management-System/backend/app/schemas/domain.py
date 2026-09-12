from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.core.geo import validate_polygon_geojson


class DriverCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    phone: str | None = Field(default=None, max_length=40)
    email: str | None = Field(default=None, max_length=255)


class DriverUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=120)
    phone: str | None = Field(default=None, max_length=40)
    email: str | None = Field(default=None, max_length=255)


class DriverRead(DriverCreate):
    model_config = ConfigDict(from_attributes=True)

    id: str
    created_at: datetime
    updated_at: datetime


class GeofenceCreate(BaseModel):
    name: str = Field(min_length=1, max_length=160)
    description: str | None = None
    boundary_geojson: dict

    @field_validator("boundary_geojson")
    @classmethod
    def validate_boundary(cls, value: dict) -> dict:
        return validate_polygon_geojson(value)


class GeofenceUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=160)
    description: str | None = None
    boundary_geojson: dict | None = None

    @field_validator("boundary_geojson")
    @classmethod
    def validate_boundary(cls, value: dict | None) -> dict | None:
        return validate_polygon_geojson(value) if value is not None else None


class GeofenceRead(GeofenceCreate):
    model_config = ConfigDict(from_attributes=True)

    id: str
    created_at: datetime
    updated_at: datetime


class TelemetryCreate(BaseModel):
    vehicle_id: str
    recorded_at: datetime
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    speed_kph: float | None = Field(default=None, ge=0)
    heading_degrees: float | None = Field(default=None, ge=0, lt=360)
    raw_payload: dict = Field(default_factory=dict)


class TelemetryRead(TelemetryCreate):
    model_config = ConfigDict(from_attributes=True)

    id: int


class TripCreate(BaseModel):
    vehicle_id: str
    name: str = Field(min_length=1, max_length=160)
    origin_latitude: float = Field(ge=-90, le=90)
    origin_longitude: float = Field(ge=-180, le=180)
    destination_latitude: float = Field(ge=-90, le=90)
    destination_longitude: float = Field(ge=-180, le=180)
    status: str = Field(default="planned", max_length=32)
    started_at: datetime | None = None
    completed_at: datetime | None = None


class TripUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=160)
    status: str | None = Field(default=None, max_length=32)
    started_at: datetime | None = None
    completed_at: datetime | None = None


class TripRead(TripCreate):
    model_config = ConfigDict(from_attributes=True)

    id: str
    created_at: datetime
    updated_at: datetime


class RoutePredictionCreate(BaseModel):
    vehicle_id: str
    destination_latitude: float = Field(ge=-90, le=90)
    destination_longitude: float = Field(ge=-180, le=180)


class RoutePredictionRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    vehicle_id: str
    origin_latitude: float
    origin_longitude: float
    destination_latitude: float
    destination_longitude: float
    distance_km: float
    eta_seconds: int
    confidence: float
    model_version: str
    created_at: datetime


class GeofenceEventRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    vehicle_id: str
    geofence_id: str
    event_type: str
    latitude: float
    longitude: float
    recorded_at: datetime
    created_at: datetime


class HealthRead(BaseModel):
    status: str
    database: str
    redis: str
    kafka: str
