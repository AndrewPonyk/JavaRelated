"""Shared fixtures: the app with infrastructure dependencies overridden by mocks.

Unit/API tests never touch real ES/PG/Redis; the full-stack integration suite
(tests/integration/test_full_stack.py, marked `integration`) uses real services.
"""

from collections.abc import AsyncIterator
from unittest.mock import AsyncMock

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.api import deps
from app.main import create_app

# A realistic ES search response (wrapped disjunctive-facet agg shape) for API tests.
ES_SEARCH_RESPONSE: dict = {
    "took": 7,
    "hits": {
        "total": {"value": 2},
        "hits": [
            {
                "_score": 12.3,
                "_source": {
                    "id": "0b6cbb26-0aa7-4d1c-8d6d-8e5a2f7f3a11",
                    "sku": "SONY-WH1000",
                    "name": "Sony WH-1000XM5 Wireless Headphones",
                    "brand": "sony",
                    "price": 349.99,
                    "category_slug": "headphones",
                    "in_stock": True,
                },
            },
            {
                "_score": 9.8,
                "_source": {
                    "id": "5f8a1f60-2c3f-4e21-9c15-40b1f13d1b22",
                    "sku": "SONY-WHCH720",
                    "name": "Sony WH-CH720N Noise Canceling Headphones",
                    "brand": "sony",
                    "price": 149.99,
                    "category_slug": "headphones",
                    "in_stock": True,
                },
            },
        ],
    },
    "aggregations": {
        "category": {
            "doc_count": 2,
            "category": {"buckets": [{"key": "headphones", "doc_count": 2}]},
        },
        "brand": {
            "doc_count": 2,
            "brand": {"buckets": [{"key": "sony", "doc_count": 2}]},
        },
        "price": {
            "doc_count": 2,
            "price": {
                "buckets": [
                    {"key": "100-250", "doc_count": 1},
                    {"key": "over-250", "doc_count": 1},
                ]
            },
        },
    },
}


@pytest.fixture
def es_mock() -> AsyncMock:
    mock = AsyncMock()
    mock.search.return_value = ES_SEARCH_RESPONSE
    return mock


@pytest.fixture
def redis_mock() -> AsyncMock:
    mock = AsyncMock()
    mock.get.return_value = None  # default: cache miss
    return mock


@pytest.fixture
def events_mock() -> AsyncMock:
    return AsyncMock()


# Named test_app (not "app") so globally installed pytest-flask never hijacks it.
@pytest.fixture
def test_app(es_mock: AsyncMock, redis_mock: AsyncMock, events_mock: AsyncMock) -> FastAPI:
    application = create_app()
    application.dependency_overrides[deps.get_es] = lambda: es_mock
    application.dependency_overrides[deps.get_redis] = lambda: redis_mock
    application.dependency_overrides[deps.get_event_service] = lambda: events_mock
    return application


@pytest.fixture
async def client(test_app: FastAPI) -> AsyncIterator[AsyncClient]:
    # ASGITransport skips lifespan — exactly what we want with mocked clients
    # (no outbox worker, no real connections).
    async with AsyncClient(transport=ASGITransport(app=test_app), base_url="http://test") as c:
        yield c
