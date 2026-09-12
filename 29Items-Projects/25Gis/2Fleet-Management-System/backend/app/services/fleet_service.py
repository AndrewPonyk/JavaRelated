from datetime import UTC, datetime

from sqlalchemy import Select, func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.api.errors import AppError
from app.core.cache import ActiveVehicleCache
from app.core.geo import haversine_km, point_in_polygon
from app.models import (
    Driver,
    Geofence,
    GeofenceEvent,
    RoutePrediction,
    Trip,
    Vehicle,
    VehicleLocationHistory,
)
from app.schemas import (
    DriverCreate,
    DriverUpdate,
    GeofenceCreate,
    GeofenceUpdate,
    RoutePredictionCreate,
    TelemetryCreate,
    TripCreate,
    TripUpdate,
    VehicleCreate,
    VehicleUpdate,
)


class FleetService:
    def __init__(self, db: Session, cache: ActiveVehicleCache | None = None) -> None:
        self.db = db
        self.cache = cache or ActiveVehicleCache()

    def list_vehicles(self, limit: int = 100, offset: int = 0) -> list[Vehicle]:
        statement = select(Vehicle).order_by(Vehicle.created_at.desc()).limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def create_vehicle(self, payload: VehicleCreate) -> Vehicle:
        if payload.driver_id:
            self._require_driver(payload.driver_id)
        vehicle = Vehicle(**payload.model_dump())
        self.db.add(vehicle)
        self._commit_or_unique("vehicle_conflict", "Vehicle license plate already exists")
        self.db.refresh(vehicle)
        return vehicle

    def get_vehicle(self, vehicle_id: str) -> Vehicle:
        return self._require_vehicle(vehicle_id)

    def update_vehicle(self, vehicle_id: str, payload: VehicleUpdate) -> Vehicle:
        vehicle = self._require_vehicle(vehicle_id)
        data = payload.model_dump(exclude_unset=True)
        if data.get("driver_id"):
            self._require_driver(data["driver_id"])
        for key, value in data.items():
            setattr(vehicle, key, value)
        self._commit_or_unique("vehicle_conflict", "Vehicle license plate already exists")
        self.db.refresh(vehicle)
        return vehicle

    def delete_vehicle(self, vehicle_id: str) -> None:
        vehicle = self._require_vehicle(vehicle_id)
        self.db.delete(vehicle)
        self.db.commit()

    def list_drivers(self, limit: int = 100, offset: int = 0) -> list[Driver]:
        statement = select(Driver).order_by(Driver.created_at.desc()).limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def create_driver(self, payload: DriverCreate) -> Driver:
        driver = Driver(**payload.model_dump())
        self.db.add(driver)
        self._commit_or_unique("driver_conflict", "Driver email already exists")
        self.db.refresh(driver)
        return driver

    def get_driver(self, driver_id: str) -> Driver:
        return self._require_driver(driver_id)

    def update_driver(self, driver_id: str, payload: DriverUpdate) -> Driver:
        driver = self._require_driver(driver_id)
        for key, value in payload.model_dump(exclude_unset=True).items():
            setattr(driver, key, value)
        self._commit_or_unique("driver_conflict", "Driver email already exists")
        self.db.refresh(driver)
        return driver

    def delete_driver(self, driver_id: str) -> None:
        driver = self._require_driver(driver_id)
        self.db.delete(driver)
        self.db.commit()

    def list_geofences(self, limit: int = 100, offset: int = 0) -> list[Geofence]:
        statement = select(Geofence).order_by(Geofence.created_at.desc()).limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def create_geofence(self, payload: GeofenceCreate) -> Geofence:
        geofence = Geofence(**payload.model_dump())
        self.db.add(geofence)
        self.db.commit()
        self.db.refresh(geofence)
        return geofence

    def get_geofence(self, geofence_id: str) -> Geofence:
        return self._require_geofence(geofence_id)

    def update_geofence(self, geofence_id: str, payload: GeofenceUpdate) -> Geofence:
        geofence = self._require_geofence(geofence_id)
        for key, value in payload.model_dump(exclude_unset=True).items():
            setattr(geofence, key, value)
        self.db.commit()
        self.db.refresh(geofence)
        return geofence

    def delete_geofence(self, geofence_id: str) -> None:
        geofence = self._require_geofence(geofence_id)
        self.db.delete(geofence)
        self.db.commit()

    def ingest_telemetry(self, payload: TelemetryCreate) -> VehicleLocationHistory:
        vehicle = self._require_vehicle(payload.vehicle_id)
        telemetry = VehicleLocationHistory(**payload.model_dump())
        vehicle.latest_latitude = payload.latitude
        vehicle.latest_longitude = payload.longitude
        vehicle.latest_speed_kph = payload.speed_kph
        vehicle.latest_heading_degrees = payload.heading_degrees
        vehicle.last_seen_at = payload.recorded_at
        vehicle.status = "active"
        self.db.add(telemetry)
        self._create_geofence_events(vehicle.id, payload)
        self._commit_or_unique("telemetry_conflict", "Telemetry already exists for this vehicle timestamp")
        self.db.refresh(telemetry)
        self.cache.set_vehicle(vehicle.id, self._vehicle_cache_payload(vehicle))
        return telemetry

    def list_vehicle_history(
        self,
        vehicle_id: str,
        limit: int = 100,
        offset: int = 0,
    ) -> list[VehicleLocationHistory]:
        self._require_vehicle(vehicle_id)
        statement = (
            select(VehicleLocationHistory)
            .where(VehicleLocationHistory.vehicle_id == vehicle_id)
            .order_by(VehicleLocationHistory.recorded_at.desc())
            .limit(limit)
            .offset(offset)
        )
        return list(self.db.scalars(statement))

    def list_trips(self, limit: int = 100, offset: int = 0) -> list[Trip]:
        statement = select(Trip).order_by(Trip.created_at.desc()).limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def create_trip(self, payload: TripCreate) -> Trip:
        self._require_vehicle(payload.vehicle_id)
        trip = Trip(**payload.model_dump())
        self.db.add(trip)
        self.db.commit()
        self.db.refresh(trip)
        return trip

    def get_trip(self, trip_id: str) -> Trip:
        return self._require_trip(trip_id)

    def update_trip(self, trip_id: str, payload: TripUpdate) -> Trip:
        trip = self._require_trip(trip_id)
        for key, value in payload.model_dump(exclude_unset=True).items():
            setattr(trip, key, value)
        self.db.commit()
        self.db.refresh(trip)
        return trip

    def delete_trip(self, trip_id: str) -> None:
        trip = self._require_trip(trip_id)
        self.db.delete(trip)
        self.db.commit()

    def trip_playback(
        self,
        trip_id: str,
        limit: int = 1000,
        offset: int = 0,
    ) -> list[VehicleLocationHistory]:
        trip = self._require_trip(trip_id)
        statement = select(VehicleLocationHistory).where(VehicleLocationHistory.vehicle_id == trip.vehicle_id)
        if trip.started_at is not None:
            statement = statement.where(VehicleLocationHistory.recorded_at >= trip.started_at)
        if trip.completed_at is not None:
            statement = statement.where(VehicleLocationHistory.recorded_at <= trip.completed_at)
        statement = statement.order_by(VehicleLocationHistory.recorded_at.asc()).limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def predict_route(self, payload: RoutePredictionCreate) -> RoutePrediction:
        vehicle = self._require_vehicle(payload.vehicle_id)
        if vehicle.latest_latitude is None or vehicle.latest_longitude is None:
            raise AppError("vehicle_location_missing", "Vehicle has no current location")

        distance_km = haversine_km(
            vehicle.latest_latitude,
            vehicle.latest_longitude,
            payload.destination_latitude,
            payload.destination_longitude,
        )
        average_speed_kph = self._historical_average_speed(payload.vehicle_id) or 45.0
        congestion_factor = 1.18 if self._is_peak_hour(datetime.now(UTC)) else 1.0
        eta_hours = distance_km / max(average_speed_kph, 5.0) * congestion_factor
        confidence = min(0.95, max(0.45, 1.0 - (distance_km / 800.0)))

        prediction = RoutePrediction(
            vehicle_id=payload.vehicle_id,
            origin_latitude=vehicle.latest_latitude,
            origin_longitude=vehicle.latest_longitude,
            destination_latitude=payload.destination_latitude,
            destination_longitude=payload.destination_longitude,
            distance_km=round(distance_km, 3),
            eta_seconds=max(60, int(eta_hours * 3600)),
            confidence=round(confidence, 3),
            model_version="heuristic-historical-traffic-v1",
        )
        self.db.add(prediction)
        self.db.commit()
        self.db.refresh(prediction)
        return prediction

    def list_route_predictions(
        self,
        vehicle_id: str | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> list[RoutePrediction]:
        statement: Select[tuple[RoutePrediction]] = select(RoutePrediction).order_by(
            RoutePrediction.created_at.desc()
        )
        if vehicle_id is not None:
            self._require_vehicle(vehicle_id)
            statement = statement.where(RoutePrediction.vehicle_id == vehicle_id)
        statement = statement.limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def list_geofence_events(
        self,
        vehicle_id: str | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> list[GeofenceEvent]:
        statement: Select[tuple[GeofenceEvent]] = select(GeofenceEvent).order_by(
            GeofenceEvent.recorded_at.desc()
        )
        if vehicle_id is not None:
            self._require_vehicle(vehicle_id)
            statement = statement.where(GeofenceEvent.vehicle_id == vehicle_id)
        statement = statement.limit(limit).offset(offset)
        return list(self.db.scalars(statement))

    def _create_geofence_events(self, vehicle_id: str, payload: TelemetryCreate) -> None:
        geofences = self.list_geofences(limit=1000)
        for geofence in geofences:
            if point_in_polygon(payload.latitude, payload.longitude, geofence.boundary_geojson):
                self.db.add(
                    GeofenceEvent(
                        vehicle_id=vehicle_id,
                        geofence_id=geofence.id,
                        event_type="inside",
                        latitude=payload.latitude,
                        longitude=payload.longitude,
                        recorded_at=payload.recorded_at,
                    )
                )

    def _historical_average_speed(self, vehicle_id: str) -> float | None:
        value = self.db.scalar(
            select(func.avg(VehicleLocationHistory.speed_kph)).where(
                VehicleLocationHistory.vehicle_id == vehicle_id,
                VehicleLocationHistory.speed_kph.is_not(None),
                VehicleLocationHistory.speed_kph > 0,
            )
        )
        return float(value) if value else None

    @staticmethod
    def _is_peak_hour(value: datetime) -> bool:
        return value.hour in {7, 8, 9, 16, 17, 18}

    def _require_vehicle(self, vehicle_id: str) -> Vehicle:
        vehicle = self.db.get(Vehicle, vehicle_id)
        if vehicle is None:
            raise AppError("vehicle_not_found", "Vehicle not found", 404)
        return vehicle

    def _require_driver(self, driver_id: str) -> Driver:
        driver = self.db.get(Driver, driver_id)
        if driver is None:
            raise AppError("driver_not_found", "Driver not found", 404)
        return driver

    def _require_geofence(self, geofence_id: str) -> Geofence:
        geofence = self.db.get(Geofence, geofence_id)
        if geofence is None:
            raise AppError("geofence_not_found", "Geofence not found", 404)
        return geofence

    def _require_trip(self, trip_id: str) -> Trip:
        trip = self.db.get(Trip, trip_id)
        if trip is None:
            raise AppError("trip_not_found", "Trip not found", 404)
        return trip

    def _commit_or_unique(self, code: str, message: str) -> None:
        try:
            self.db.commit()
        except IntegrityError as exc:
            self.db.rollback()
            raise AppError(code, message, 409) from exc

    @staticmethod
    def _vehicle_cache_payload(vehicle: Vehicle) -> dict:
        return {
            "id": vehicle.id,
            "name": vehicle.name,
            "license_plate": vehicle.license_plate,
            "status": vehicle.status,
            "latest_latitude": vehicle.latest_latitude,
            "latest_longitude": vehicle.latest_longitude,
            "last_seen_at": vehicle.last_seen_at,
        }
