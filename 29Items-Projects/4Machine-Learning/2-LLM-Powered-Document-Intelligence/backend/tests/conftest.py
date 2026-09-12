"""Shared pytest fixtures.

Tests run fully offline against the ``local`` RAG backend (real hashing embeddings +
in-memory cosine store + extractive QA) — no Bedrock/Pinecone/AWS. Each test gets its own
SQLite file (schema built with a sync engine; the app uses an async engine over the same
file) so there is never cross-test lock contention.
"""

from __future__ import annotations

import asyncio
import os
import tempfile

import pytest
import pytest_asyncio
from sqlalchemy import create_engine
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.core.config import settings

# ── Configure settings for an isolated, offline test run ───────────────────
_TMP = tempfile.mkdtemp(prefix="docintel-test-")

settings.app_env = "test"
settings.rag_backend = "local"
settings.storage_backend = "local"
settings.ingest_mode = "inline"
settings.local_storage_dir = os.path.join(_TMP, "documents")
settings.jwt_jwks_url = ""  # force the dev-token verification path

from app.core.security import make_dev_token  # noqa: E402
from app.db.session import get_session  # noqa: E402
from app.main import create_app  # noqa: E402
from app.models import Base  # noqa: E402
from app.rag.embeddings import get_embedder  # noqa: E402
from app.rag.llm import get_chat_model  # noqa: E402
from app.rag.vector_store import get_vector_store, reset_in_memory_store  # noqa: E402
from app.storage import get_storage  # noqa: E402

# Providers are cached lru — rebuild them against the test settings.
for _factory in (get_storage, get_embedder, get_vector_store, get_chat_model):
    _factory.cache_clear()


@pytest.fixture
def _sessionmaker(tmp_path):
    """A per-test async sessionmaker over a fresh SQLite file (schema pre-created)."""
    db_path = tmp_path / "test.db"
    sync_engine = create_engine(f"sqlite:///{db_path}")
    Base.metadata.create_all(sync_engine)
    sync_engine.dispose()

    async_engine = create_async_engine(
        f"sqlite+aiosqlite:///{db_path}", poolclass=NullPool, connect_args={"timeout": 30}
    )
    yield async_sessionmaker(async_engine, expire_on_commit=False)
    asyncio.run(async_engine.dispose())


@pytest.fixture(autouse=True)
def _clean_vectors():
    reset_in_memory_store()
    yield
    reset_in_memory_store()


@pytest.fixture
def fastapi_app(_sessionmaker):
    async def _override_get_session():
        async with _sessionmaker() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    application = create_app()
    application.dependency_overrides[get_session] = _override_get_session
    return application


@pytest.fixture
def client(fastapi_app):
    from fastapi.testclient import TestClient

    with TestClient(fastapi_app) as test_client:
        yield test_client


@pytest_asyncio.fixture
async def db_session(_sessionmaker):
    async with _sessionmaker() as session:
        yield session
        await session.commit()


@pytest.fixture
def auth_headers() -> dict[str, str]:
    """Bearer header for the default test tenant 'tenant-a' (all scopes)."""
    return {"Authorization": f"Bearer {make_dev_token('tenant-a')}"}


def headers_for(tenant: str, scopes: tuple[str, ...] | None = None) -> dict[str, str]:
    """Build an auth header for an arbitrary tenant / scope set."""
    if scopes is None:
        return {"Authorization": f"Bearer {make_dev_token(tenant)}"}
    return {"Authorization": f"Bearer {make_dev_token(tenant, scopes=scopes)}"}
