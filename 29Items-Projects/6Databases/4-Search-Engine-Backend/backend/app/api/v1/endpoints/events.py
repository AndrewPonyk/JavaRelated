"""POST /api/v1/events/click — result-click feedback (LTR training signal)."""

from fastapi import APIRouter, Depends, Header, status

from app.api.deps import get_event_service
from app.schemas.events import ClickEventIn
from app.services.event_service import EventService

router = APIRouter()


@router.post("/click", status_code=status.HTTP_204_NO_CONTENT, summary="Log a result click")
async def log_click(
    payload: ClickEventIn,
    x_session_id: str | None = Header(default=None, max_length=64),
    events: EventService = Depends(get_event_service),
) -> None:
    await events.log_click(
        query=payload.query,
        product_id=payload.product_id,
        position=payload.position,
        session_id=x_session_id or "anonymous",
    )
