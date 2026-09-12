"""FastAPI dependency providers — the composition root for request-scoped objects."""

import hmac
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import Depends, Header, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.core.exceptions import UnauthorizedError
from app.db.session import async_session_factory
from app.repositories.product_repository import ProductRepository
from app.services.event_service import EventService
from app.services.product_service import ProductService
from app.services.search_service import SearchService
from app.services.suggest_service import SuggestService

SettingsDep = Annotated[Settings, Depends(get_settings)]


async def get_db() -> AsyncIterator[AsyncSession]:
    async with async_session_factory() as session:
        yield session


def get_es(request: Request):  # type: ignore[no-untyped-def]  # AsyncElasticsearch
    return request.app.state.es


def get_redis(request: Request):  # type: ignore[no-untyped-def]  # redis.asyncio.Redis
    return request.app.state.redis


def get_event_service() -> EventService:
    return EventService()


def get_search_service(  # type: ignore[no-untyped-def]
    es=Depends(get_es),
    settings: Settings = Depends(get_settings),
    events: EventService = Depends(get_event_service),
) -> SearchService:
    return SearchService(es=es, settings=settings, events=events)


def get_suggest_service(  # type: ignore[no-untyped-def]
    es=Depends(get_es),
    redis=Depends(get_redis),
    settings: Settings = Depends(get_settings),
) -> SuggestService:
    return SuggestService(es=es, redis=redis, settings=settings)


def get_product_service(db: AsyncSession = Depends(get_db)) -> ProductService:
    return ProductService(repository=ProductRepository(db))


async def require_admin_key(
    settings: SettingsDep,
    x_api_key: str = Header(default=""),
) -> None:
    """Constant-time API-key check for admin/write endpoints. Operator access moves
    behind Google IAM/IAP once the service runs on Cloud Run (TECH-NOTES §3.3)."""
    if not hmac.compare_digest(x_api_key, settings.admin_api_key):
        raise UnauthorizedError("Invalid or missing X-API-Key header")
