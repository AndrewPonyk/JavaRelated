"""Pytest fixtures: isolated async DB (SQLite) + API client with REAL JWTs.

Scanner clients are ALWAYS mocked here (respx) — real ZAP/SQLMap only run in
the docker-compose stack (TECH-NOTES 3.2).
"""

from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
import pytest_asyncio
from app.core import security
from app.core.ratelimit import limiter
from app.db.base import Base
from app.db.session import get_session
from app.main import create_app
from app.models.user import User
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

# ── DB ─────────────────────────────────────────────────────────────────


@pytest_asyncio.fixture
async def db_engine():
    # StaticPool: one shared in-memory connection so every session sees the
    # same tables (async sqlite default pool would give each session its own
    # empty DB).
    from sqlalchemy.pool import StaticPool

    engine = create_async_engine(
        "sqlite+aiosqlite://",
        poolclass=StaticPool,
        connect_args={"check_same_thread": False},
    )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield engine
    await engine.dispose()


@pytest_asyncio.fixture
async def session_factory(db_engine) -> async_sessionmaker:
    return async_sessionmaker(db_engine, expire_on_commit=False)


@pytest_asyncio.fixture
async def db_session(session_factory) -> AsyncIterator:
    async with session_factory() as session:
        yield session


# ── app/client ─────────────────────────────────────────────────────────


@pytest_asyncio.fixture
async def client(session_factory, monkeypatch) -> AsyncIterator[AsyncClient]:
    """Fresh app + API client wired to the test DB.

    - get_session dependency overridden per-request (routers)
    - app.db.session.SessionFactory patched for code that opens its own
      sessions (API-key auth path)
    - PBKDF2 iterations dropped to 1k so the suite stays fast
    - rate-limiter buckets cleared between tests
    """
    monkeypatch.setattr(security, "_PBKDF2_ITERATIONS", 1_000)
    limiter._buckets.clear()
    monkeypatch.setattr("app.db.session.SessionFactory", session_factory)

    app = create_app()

    async def _override():
        async with session_factory() as session:
            yield session

    app.dependency_overrides[get_session] = _override
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()


# ── helpers ────────────────────────────────────────────────────────────


async def make_user(
    session_factory,
    email: str = "user@test.dev",
    role: str = "viewer",
    password: str = "password-123",
    is_active: bool = True,
) -> User:
    async with session_factory() as session:
        user = User(
            email=email,
            full_name=email.split("@")[0].title(),
            hashed_password=security.hash_password(password),
            role=role,
            is_active=is_active,
        )
        session.add(user)
        await session.commit()
        await session.refresh(user)
        return user


def auth_headers(user: User) -> dict[str, str]:
    token = security.create_access_token(user.id, user.role)
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def sample_alert_payload() -> dict:
    """Recorded-shape ZAP alert fixture for client/orchestrator tests."""
    return {
        "pluginId": "40018",
        "name": "SQL Injection",
        "risk": "high",
        "url": "https://target.example.com/items?id=1",
        "param": "id",
        "method": "GET",
        "cweid": "89",
        "description": "SQL injection may be possible.",
        "evidence": "OR 1=1",
    }
