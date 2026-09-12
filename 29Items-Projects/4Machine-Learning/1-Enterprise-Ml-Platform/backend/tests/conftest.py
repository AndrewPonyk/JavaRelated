"""Shared pytest fixtures: env, async test DB, app client, auth, isolation.

Tests run against an in-memory SQLite database (shared across connections via
StaticPool) created fresh per test, so they need no external services and stay
isolated. The app's ``get_session`` dependency is overridden to use it.
"""
from __future__ import annotations

import os
import time
from collections.abc import AsyncIterator, Iterator

import pytest

# Configure a deterministic env BEFORE any app module reads settings.
os.environ.setdefault("APP_ENV", "dev")
os.environ.setdefault("AUTH_MODE", "hs256")
os.environ.setdefault("JWT_SECRET", "test-secret")
os.environ.setdefault("OIDC_AUDIENCE", "enterprise-ml-platform")
os.environ.setdefault("DATABASE_URL", "sqlite+aiosqlite://")

from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from src.api.main import create_app  # noqa: E402
from src.core.config import get_settings  # noqa: E402
from src.core.security import encode_hs256  # noqa: E402
from src.db.models import Base  # noqa: E402
from src.db.repository import SqlAlchemyRepository  # noqa: E402
from src.db.session import get_session  # noqa: E402
from src.db.telemetry import TelemetryBuffer, get_telemetry  # noqa: E402

ALL_SCOPES = ["experiments:read", "experiments:write", "models:promote", "serving:invoke"]


@pytest.fixture(autouse=True)
def _reset_telemetry() -> Iterator[None]:
    get_telemetry().reset()
    yield
    get_telemetry().reset()


@pytest.fixture
async def engine() -> AsyncIterator[object]:
    """A fresh in-memory SQLite engine with tables created, per test."""
    eng = create_async_engine(
        "sqlite+aiosqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with eng.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield eng
    await eng.dispose()


@pytest.fixture
def sessionmaker_(engine) -> async_sessionmaker:
    return async_sessionmaker(engine, expire_on_commit=False)


@pytest.fixture
async def db_session(sessionmaker_) -> AsyncIterator[object]:
    async with sessionmaker_() as session:
        yield session


@pytest.fixture
def repo(db_session) -> SqlAlchemyRepository:
    return SqlAlchemyRepository(db_session)


@pytest.fixture
def telemetry() -> TelemetryBuffer:
    """A fresh, isolated telemetry buffer for unit tests."""
    return TelemetryBuffer()


def make_token(scopes: list[str] | None = None, *, expired: bool = False) -> str:
    settings = get_settings()
    now = int(time.time())
    return encode_hs256(
        {
            "sub": "user-test",
            "email": "tester@example.com",
            "aud": settings.oidc_audience,
            "scope": " ".join(ALL_SCOPES if scopes is None else scopes),
            "exp": now - 10 if expired else now + 3600,
        },
        settings.jwt_secret,
    )


@pytest.fixture
def auth_headers() -> dict[str, str]:
    """Authorization header carrying all platform scopes."""
    return {"Authorization": f"Bearer {make_token()}"}


@pytest.fixture
async def client(sessionmaker_) -> AsyncIterator[AsyncClient]:
    """App client wired to the test database via dependency override."""
    app = create_app()

    async def _override_session() -> AsyncIterator[object]:
        async with sessionmaker_() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    app.dependency_overrides[get_session] = _override_session
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c
