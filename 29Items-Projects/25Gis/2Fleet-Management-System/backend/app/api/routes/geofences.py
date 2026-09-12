from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db.session import get_db
from app.schemas import GeofenceCreate, GeofenceEventRead, GeofenceRead, GeofenceUpdate
from app.services.fleet_service import FleetService

router = APIRouter(prefix="/geofences", tags=["geofences"], dependencies=[Depends(get_current_user)])


def get_service(db: Session = Depends(get_db)) -> FleetService:
    return FleetService(db)


@router.get("", response_model=list[GeofenceRead])
async def list_geofences(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    service: FleetService = Depends(get_service),
) -> list[GeofenceRead]:
    return service.list_geofences(limit=limit, offset=offset)


@router.post("", response_model=GeofenceRead, status_code=status.HTTP_201_CREATED)
async def create_geofence(
    payload: GeofenceCreate,
    service: FleetService = Depends(get_service),
) -> GeofenceRead:
    return service.create_geofence(payload)


@router.get("/events", response_model=list[GeofenceEventRead])
async def list_geofence_events(
    vehicle_id: str | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=1000),
    offset: int = Query(default=0, ge=0),
    service: FleetService = Depends(get_service),
) -> list[GeofenceEventRead]:
    return service.list_geofence_events(vehicle_id=vehicle_id, limit=limit, offset=offset)


@router.get("/{geofence_id}", response_model=GeofenceRead)
async def get_geofence(
    geofence_id: str,
    service: FleetService = Depends(get_service),
) -> GeofenceRead:
    return service.get_geofence(geofence_id)


@router.patch("/{geofence_id}", response_model=GeofenceRead)
async def update_geofence(
    geofence_id: str,
    payload: GeofenceUpdate,
    service: FleetService = Depends(get_service),
) -> GeofenceRead:
    return service.update_geofence(geofence_id, payload)


@router.delete("/{geofence_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_geofence(
    geofence_id: str,
    service: FleetService = Depends(get_service),
) -> None:
    service.delete_geofence(geofence_id)
