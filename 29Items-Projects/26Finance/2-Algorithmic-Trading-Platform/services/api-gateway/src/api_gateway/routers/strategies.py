"""Strategy CRUD routes (thin HTTP layer over StrategyService)."""

from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from api_gateway.dependencies import get_session, require_role
from api_gateway.schemas.strategy import StrategyCreate, StrategyOut, StrategyUpdate
from api_gateway.services.strategy_service import (
    DuplicateStrategyError,
    InvalidStateTransitionError,
    StrategyNotFoundError,
    StrategyService,
)

router = APIRouter(prefix="/strategies", tags=["strategies"])


def _service(session: AsyncSession = Depends(get_session)) -> StrategyService:
    return StrategyService(session)


@router.get("", response_model=list[StrategyOut])
async def list_strategies(svc: StrategyService = Depends(_service)) -> list[StrategyOut]:
    return await svc.list()


@router.get("/{strategy_id}", response_model=StrategyOut)
async def get_strategy(strategy_id: UUID, svc: StrategyService = Depends(_service)) -> StrategyOut:
    try:
        return await svc.get(strategy_id)
    except StrategyNotFoundError:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "strategy not found") from None


@router.post(
    "",
    response_model=StrategyOut,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_role("trader"))],
)
async def create_strategy(
    payload: StrategyCreate, svc: StrategyService = Depends(_service)
) -> StrategyOut:
    try:
        return await svc.create(payload)
    except DuplicateStrategyError:
        raise HTTPException(status.HTTP_409_CONFLICT, "strategy name already exists") from None


@router.patch(
    "/{strategy_id}",
    response_model=StrategyOut,
    dependencies=[Depends(require_role("trader"))],
)
async def update_strategy(
    strategy_id: UUID, patch: StrategyUpdate, svc: StrategyService = Depends(_service)
) -> StrategyOut:
    try:
        return await svc.update(strategy_id, patch)
    except StrategyNotFoundError:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "strategy not found") from None
    except DuplicateStrategyError:
        raise HTTPException(status.HTTP_409_CONFLICT, "strategy name already exists") from None
    except InvalidStateTransitionError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, str(exc)) from None


@router.delete(
    "/{strategy_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(require_role("admin"))],
)
async def delete_strategy(strategy_id: UUID, svc: StrategyService = Depends(_service)) -> Response:
    try:
        await svc.delete(strategy_id)
    except StrategyNotFoundError:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "strategy not found") from None
    except InvalidStateTransitionError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, str(exc)) from None
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post(
    "/{strategy_id}/halt",
    response_model=StrategyOut,
    dependencies=[Depends(require_role("trader"))],
)
async def halt_strategy(strategy_id: UUID, svc: StrategyService = Depends(_service)) -> StrategyOut:
    """Kill-switch — requires the `trader` role."""
    try:
        return await svc.halt(strategy_id)
    except StrategyNotFoundError:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "strategy not found") from None
