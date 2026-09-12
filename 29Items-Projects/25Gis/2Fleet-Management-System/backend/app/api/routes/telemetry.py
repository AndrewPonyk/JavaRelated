from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db.session import get_db
from app.schemas import TelemetryCreate, TelemetryRead
from app.services.fleet_service import FleetService

router = APIRouter(prefix="/telemetry", tags=["telemetry"], dependencies=[Depends(get_current_user)])


def get_service(db: Session = Depends(get_db)) -> FleetService:
    return FleetService(db)


@router.post("", response_model=TelemetryRead, status_code=status.HTTP_201_CREATED)
async def ingest_telemetry(
    payload: TelemetryCreate,
    service: FleetService = Depends(get_service),
) -> TelemetryRead:
    return service.ingest_telemetry(payload)
