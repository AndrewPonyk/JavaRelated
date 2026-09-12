from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db.session import get_db
from app.schemas import TelemetryRead, TripCreate, TripRead, TripUpdate
from app.services.fleet_service import FleetService

router = APIRouter(prefix="/trips", tags=["trips"], dependencies=[Depends(get_current_user)])


def get_service(db: Session = Depends(get_db)) -> FleetService:
    return FleetService(db)


@router.get("", response_model=list[TripRead])
async def list_trips(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    service: FleetService = Depends(get_service),
) -> list[TripRead]:
    return service.list_trips(limit=limit, offset=offset)


@router.post("", response_model=TripRead, status_code=status.HTTP_201_CREATED)
async def create_trip(payload: TripCreate, service: FleetService = Depends(get_service)) -> TripRead:
    return service.create_trip(payload)


@router.get("/{trip_id}", response_model=TripRead)
async def get_trip(trip_id: str, service: FleetService = Depends(get_service)) -> TripRead:
    return service.get_trip(trip_id)


@router.patch("/{trip_id}", response_model=TripRead)
async def update_trip(
    trip_id: str,
    payload: TripUpdate,
    service: FleetService = Depends(get_service),
) -> TripRead:
    return service.update_trip(trip_id, payload)


@router.delete("/{trip_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_trip(trip_id: str, service: FleetService = Depends(get_service)) -> None:
    service.delete_trip(trip_id)


@router.get("/{trip_id}/playback", response_model=list[TelemetryRead])
async def trip_playback(
    trip_id: str,
    limit: int = Query(default=1000, ge=1, le=5000),
    offset: int = Query(default=0, ge=0),
    service: FleetService = Depends(get_service),
) -> list[TelemetryRead]:
    return service.trip_playback(trip_id, limit=limit, offset=offset)
