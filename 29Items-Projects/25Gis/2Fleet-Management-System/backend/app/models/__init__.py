"""SQLAlchemy model exports."""

from app.models.base import Base
from app.models.domain import (
    Driver,
    Geofence,
    GeofenceEvent,
    RoutePrediction,
    Trip,
    Vehicle,
    VehicleLocationHistory,
)

__all__ = [
    "Base",
    "Driver",
    "Geofence",
    "GeofenceEvent",
    "RoutePrediction",
    "Trip",
    "Vehicle",
    "VehicleLocationHistory",
]
