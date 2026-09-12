"""True end-to-end tests against real Elasticsearch + PostgreSQL + Redis.

Covers what mocks cannot: analyzer behavior (synonyms, stemming), the completion
suggester, the transactional outbox drain, and PG-backed CRUD.

Gated: runs only when RUN_INTEGRATION=1 and the services are up (docker compose
up locally, service containers in CI). Setup (migrations + index bootstrap) is
executed by the session fixture below.
"""

import asyncio
import os
import subprocess
import sys
import time
import uuid
from collections.abc import AsyncIterator
from pathlib import Path

import pytest
from httpx import ASGITransport, AsyncClient

pytestmark = [
    pytest.mark.integration,
    pytest.mark.skipif(
        os.getenv("RUN_INTEGRATION") != "1",
        reason="needs real services: RUN_INTEGRATION=1 + docker compose up",
    ),
]

BACKEND_DIR = Path(__file__).resolve().parents[2]
REPO_ROOT = BACKEND_DIR.parent
ADMIN = {"X-API-Key": "change-me"}


@pytest.fixture(scope="session", autouse=True)
def bootstrap_infrastructure() -> None:
    """Apply migrations and create the index/synonyms exactly like a deploy does."""
    if os.getenv("RUN_INTEGRATION") != "1":
        return
    subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "head"], cwd=BACKEND_DIR, check=True
    )
    subprocess.run(
        [sys.executable, str(REPO_ROOT / "scripts" / "create_indexes.py")],
        cwd=REPO_ROOT,
        check=True,
    )


@pytest.fixture(autouse=True)
async def fresh_pg_pool() -> AsyncIterator[None]:
    """pytest-asyncio runs each test in its own event loop, but the engine pool is
    module-global — dispose it per test so no connection outlives its loop."""
    yield
    from app.db.session import engine

    await engine.dispose()


@pytest.fixture
async def live_client() -> AsyncIterator[AsyncClient]:
    """The real app wired to real infrastructure (lifespan replicated manually,
    without the background outbox loop — tests drain deterministically instead)."""
    from app.cache.redis_client import create_redis_client
    from app.core.config import get_settings
    from app.main import create_app
    from app.search.es_client import create_es_client

    settings = get_settings()
    app = create_app()
    app.state.es = create_es_client(settings)
    app.state.redis = create_redis_client(settings)
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
            c.app_state = app.state  # type: ignore[attr-defined]
            yield c
    finally:
        await app.state.es.close()
        await app.state.redis.aclose()


async def drain_and_refresh(app_state) -> None:  # type: ignore[no-untyped-def]
    """Run what the background worker does, then refresh ES.

    The dev-stack backend container runs its OWN worker against the same DB; a row
    it has claimed (SKIP LOCKED) is invisible to our drain but may not be committed
    to ES yet. So after draining what we can, wait until nothing is pending —
    whichever worker processed it — before refreshing.
    """
    from sqlalchemy import func, select

    from app.core.config import get_settings
    from app.db.models.index_outbox import IndexOutbox
    from app.db.session import async_session_factory
    from app.services.indexing_service import IndexingService
    from app.services.outbox_worker import drain_pending

    settings = get_settings()
    indexing = IndexingService(es=app_state.es, settings=settings)
    async with async_session_factory() as session:
        while await drain_pending(session, indexing):
            pass

    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        async with async_session_factory() as session:
            pending = await session.scalar(
                select(func.count())
                .select_from(IndexOutbox)
                .where(
                    IndexOutbox.processed_at.is_(None),
                    IndexOutbox.attempts < settings.outbox_max_attempts,
                )
            )
        if not pending:
            break
        await asyncio.sleep(0.2)

    await app_state.es.indices.refresh(index=settings.es_products_alias)


async def test_readiness_reports_all_dependencies(live_client: AsyncClient) -> None:
    response = await live_client.get("/readyz")
    assert response.status_code == 200
    checks = response.json()["checks"]
    assert checks == {"elasticsearch": True, "redis": True, "postgres": True}


async def test_product_lifecycle_reaches_the_index(live_client: AsyncClient) -> None:
    marker = uuid.uuid4().hex[:8]
    payload = {
        "sku": f"IT-{marker}",
        "name": f"Integrino Wireless Headphones {marker}",
        "description": "Integration test product",
        "brand": "integrino",
        "price": "129.99",
        "attributes": {"color": "blue"},
    }
    created = await live_client.post("/api/v1/products", json=payload, headers=ADMIN)
    assert created.status_code == 201, created.text
    product_id = created.json()["id"]

    await drain_and_refresh(live_client.app_state)  # type: ignore[attr-defined]

    # Searchable (stemming: "headphone" matches "Headphones") with facets.
    result = await live_client.get("/api/v1/search", params={"q": f"headphone {marker}"})
    assert result.status_code == 200
    data = result.json()
    assert any(hit["id"] == product_id for hit in data["hits"])
    brand_facet = next(f for f in data["facets"] if f["name"] == "brand")
    assert any(v["value"] == "integrino" for v in brand_facet["values"])

    # Autocomplete from the completion suggester (then served from Redis cache).
    for _ in range(2):
        suggest = await live_client.get("/api/v1/suggest", params={"q": "integrino"})
        assert suggest.status_code == 200
        assert any("integrino" in s.lower() for s in suggest.json()["suggestions"])

    # Soft delete propagates as an index delete.
    assert (
        await live_client.delete(f"/api/v1/products/{product_id}", headers=ADMIN)
    ).status_code == 204
    await drain_and_refresh(live_client.app_state)  # type: ignore[attr-defined]
    gone = await live_client.get("/api/v1/search", params={"q": marker})
    assert all(hit["id"] != product_id for hit in gone.json()["hits"])


async def test_synonyms_match_at_search_time(live_client: AsyncClient) -> None:
    marker = uuid.uuid4().hex[:8]
    payload = {
        "sku": f"TV-{marker}",
        "name": f"Grandview Television {marker}",
        "brand": "grandview",
        "price": "799.00",
    }
    created = await live_client.post("/api/v1/products", json=payload, headers=ADMIN)
    assert created.status_code == 201
    product_id = created.json()["id"]

    await drain_and_refresh(live_client.app_state)  # type: ignore[attr-defined]

    # "telly" never appears in the document — only the synonyms set connects it.
    result = await live_client.get("/api/v1/search", params={"q": f"telly {marker}"})
    assert any(hit["id"] == product_id for hit in result.json()["hits"])


async def test_click_events_land_in_postgres(live_client: AsyncClient) -> None:
    from sqlalchemy import func, select

    from app.db.models.search_event import SearchEvent
    from app.db.session import async_session_factory

    marker = uuid.uuid4()
    response = await live_client.post(
        "/api/v1/events/click",
        json={"query": "integration click", "product_id": str(marker), "position": 1},
        headers={"X-Session-ID": "it-session"},
    )
    assert response.status_code == 204

    async with async_session_factory() as session:
        count = await session.scalar(
            select(func.count())
            .select_from(SearchEvent)
            .where(SearchEvent.clicked_product_id == str(marker))
        )
    assert count == 1
