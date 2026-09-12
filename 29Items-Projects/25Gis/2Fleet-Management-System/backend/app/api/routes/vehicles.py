from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db.session import get_db
from app.schemas import TelemetryRead, VehicleCreate, VehicleRead, VehicleUpdate
from app.services.fleet_service import FleetService

router = APIRouter(
    prefix="/vehicles",
    tags=["vehicles"],
    dependencies=[Depends(get_current_user)],
)


def get_service(db: Session = Depends(get_db)) -> FleetService:
    return FleetService(db)


@router.get("", response_model=list[VehicleRead])
async def list_vehicles(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    service: FleetService = Depends(get_service),
) -> list[VehicleRead]:
    return service.list_vehicles(limit=limit, offset=offset)


@router.post("", response_model=VehicleRead, status_code=status.HTTP_201_CREATED)
async def create_vehicle(
    payload: VehicleCreate,
    service: FleetService = Depends(get_service),
) -> VehicleRead:
    return service.create_vehicle(payload)


@router.get("/{vehicle_id}", response_model=VehicleRead)
async def get_vehicle(
    vehicle_id: str,
    service: FleetService = Depends(get_service),
) -> VehicleRead:
    return service.get_vehicle(vehicle_id)


@router.patch("/{vehicle_id}", response_model=VehicleRead)
async def update_vehicle(
    vehicle_id: str,
    payload: VehicleUpdate,
    service: FleetService = Depends(get_service),
) -> VehicleRead:
    return service.update_vehicle(vehicle_id, payload)


@router.delete("/{vehicle_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_vehicle(
    vehicle_id: str,
    service: FleetService = Depends(get_service),
) -> None:
    service.delete_vehicle(vehicle_id)


@router.get("/{vehicle_id}/history", response_model=list[TelemetryRead])
async def list_vehicle_history(
    vehicle_id: str,
    limit: int = Query(default=100, ge=1, le=1000),
    offset: int = Query(default=0, ge=0),
    service: FleetService = Depends(get_service),
) -> list[TelemetryRead]:
    return service.list_vehicle_history(vehicle_id, limit=limit, offset=offset)
