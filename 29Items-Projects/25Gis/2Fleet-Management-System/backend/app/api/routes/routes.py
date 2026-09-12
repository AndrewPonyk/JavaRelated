from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db.session import get_db
from app.schemas import RoutePredictionCreate, RoutePredictionRead
from app.services.fleet_service import FleetService

router = APIRouter(prefix="/routes", tags=["routes"], dependencies=[Depends(get_current_user)])


def get_service(db: Session = Depends(get_db)) -> FleetService:
    return FleetService(db)


@router.post("/predict", response_model=RoutePredictionRead, status_code=status.HTTP_201_CREATED)
async def predict_route(
    payload: RoutePredictionCreate,
    service: FleetService = Depends(get_service),
) -> RoutePredictionRead:
    return service.predict_route(payload)


@router.get("/predictions", response_model=list[RoutePredictionRead])
async def list_route_predictions(
    vehicle_id: str | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    service: FleetService = Depends(get_service),
) -> list[RoutePredictionRead]:
    return service.list_route_predictions(vehicle_id=vehicle_id, limit=limit, offset=offset)
