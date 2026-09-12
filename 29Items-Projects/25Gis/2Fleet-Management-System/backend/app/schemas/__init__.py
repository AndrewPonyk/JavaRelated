"""Pydantic schema exports."""

from app.schemas.domain import (
    DriverCreate,
    DriverRead,
    DriverUpdate,
    GeofenceCreate,
    GeofenceEventRead,
    GeofenceRead,
    GeofenceUpdate,
    HealthRead,
    RoutePredictionCreate,
    RoutePredictionRead,
    TelemetryCreate,
    TelemetryRead,
    TripCreate,
    TripRead,
    TripUpdate,
)
from app.schemas.vehicle import VehicleCreate, VehicleRead, VehicleUpdate

__all__ = [
    "DriverCreate",
    "DriverRead",
    "DriverUpdate",
    "GeofenceCreate",
    "GeofenceEventRead",
    "GeofenceRead",
    "GeofenceUpdate",
    "HealthRead",
    "RoutePredictionCreate",
    "RoutePredictionRead",
    "TelemetryCreate",
    "TelemetryRead",
    "TripCreate",
    "TripRead",
    "TripUpdate",
    "VehicleCreate",
    "VehicleRead",
    "VehicleUpdate",
]
