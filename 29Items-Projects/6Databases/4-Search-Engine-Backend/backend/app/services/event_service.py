"""Search interaction logging — the raw signal for LTR training and popularity.

Search impressions are logged fire-and-forget (never in the request's latency path);
click events arrive on their own endpoint. Both write to search_events with their own
sessions, are PII-free (session pseudonym only), and swallow failures: losing a log
line must never fail a user request.
"""

import asyncio
from collections.abc import Callable
from typing import Any
from uuid import UUID

import structlog
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.search_event import SearchEvent
from app.db.session import async_session_factory

logger = structlog.get_logger(__name__)

MAX_NORMALIZED_LEN = 512

# Strong references so fire-and-forget tasks are not garbage-collected mid-flight.
_background_tasks: set[asyncio.Task[Any]] = set()


def normalize_query(query: str) -> str:
    """Canonical query form for aggregation: lowercase, collapsed whitespace."""
    return " ".join(query.lower().split())[:MAX_NORMALIZED_LEN]


def fire_and_forget(coro: Any) -> None:
    task = asyncio.create_task(coro)
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)


class EventService:
    def __init__(self, session_factory: Callable[[], AsyncSession] = async_session_factory) -> None:
        self._session_factory = session_factory

    async def log_search(
        self,
        *,
        query: str,
        filters: dict[str, list[str]],
        results_count: int,
        shown_product_ids: list[str],
        session_id: str,
    ) -> None:
        event = SearchEvent(
            query=query[:2000],
            normalized_query=normalize_query(query),
            filters=filters,
            results_count=results_count,
            shown_product_ids=shown_product_ids,
            session_id=session_id[:64],
        )
        await self._write(event)

    async def log_click(
        self,
        *,
        query: str,
        product_id: UUID,
        position: int,
        session_id: str,
    ) -> None:
        event = SearchEvent(
            query=query[:2000],
            normalized_query=normalize_query(query),
            filters={},
            results_count=0,
            clicked_product_id=str(product_id),
            clicked_position=position,
            session_id=session_id[:64],
        )
        await self._write(event)

    async def _write(self, event: SearchEvent) -> None:
        try:
            async with self._session_factory() as session:
                session.add(event)
                await session.commit()
        except Exception as exc:  # noqa: BLE001 — logging must never break serving
            logger.warning("search_event_write_failed", error=str(exc))
