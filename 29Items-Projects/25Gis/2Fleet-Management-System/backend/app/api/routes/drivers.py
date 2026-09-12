from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db.session import get_db
from app.schemas import DriverCreate, DriverRead, DriverUpdate
from app.services.fleet_service import FleetService

router = APIRouter(prefix="/drivers", tags=["drivers"], dependencies=[Depends(get_current_user)])


def get_service(db: Session = Depends(get_db)) -> FleetService:
    return FleetService(db)


@router.get("", response_model=list[DriverRead])
async def list_drivers(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    service: FleetService = Depends(get_service),
) -> list[DriverRead]:
    return service.list_drivers(limit=limit, offset=offset)


@router.post("", response_model=DriverRead, status_code=status.HTTP_201_CREATED)
async def create_driver(
    payload: DriverCreate,
    service: FleetService = Depends(get_service),
) -> DriverRead:
    return service.create_driver(payload)


@router.get("/{driver_id}", response_model=DriverRead)
async def get_driver(driver_id: str, service: FleetService = Depends(get_service)) -> DriverRead:
    return service.get_driver(driver_id)


@router.patch("/{driver_id}", response_model=DriverRead)
async def update_driver(
    driver_id: str,
    payload: DriverUpdate,
    service: FleetService = Depends(get_service),
) -> DriverRead:
    return service.update_driver(driver_id, payload)


@router.delete("/{driver_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_driver(driver_id: str, service: FleetService = Depends(get_service)) -> None:
    service.delete_driver(driver_id)
