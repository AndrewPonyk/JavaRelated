"""SuggestService: cache-aside behavior with mocked ES and Redis."""

import json
from unittest.mock import AsyncMock

import pytest

from app.core.config import Settings
from app.services.suggest_service import SuggestService


@pytest.fixture
def settings() -> Settings:
    return Settings(suggest_cache_ttl_s=300, suggest_cache_jitter_s=0)


def make_service(settings: Settings) -> tuple[SuggestService, AsyncMock, AsyncMock]:
    es, redis = AsyncMock(), AsyncMock()
    return SuggestService(es=es, redis=redis, settings=settings), es, redis


async def test_cache_hit_skips_elasticsearch(settings: Settings) -> None:
    service, es, redis = make_service(settings)
    redis.get.return_value = json.dumps(["laptop", "laptop stand"])

    result = await service.suggest("Lapt")

    assert result == ["laptop", "laptop stand"]
    es.search.assert_not_awaited()


async def test_cache_miss_queries_es_and_caches(settings: Settings) -> None:
    service, es, redis = make_service(settings)
    redis.get.return_value = None
    es.search.return_value = {"suggest": {"product_suggest": [{"options": [{"text": "laptop"}]}]}}

    result = await service.suggest("lapt")

    assert result == ["laptop"]
    es.search.assert_awaited_once()
    redis.setex.assert_awaited_once()
    key, ttl, payload = redis.setex.await_args.args
    assert key == "suggest:v1:lapt:8"
    assert ttl == 300
    assert json.loads(payload) == ["laptop"]


async def test_short_prefix_returns_empty_without_io(settings: Settings) -> None:
    service, es, redis = make_service(settings)

    assert await service.suggest("l") == []
    redis.get.assert_not_awaited()
    es.search.assert_not_awaited()


async def test_redis_failure_degrades_to_es(settings: Settings) -> None:
    service, es, redis = make_service(settings)
    redis.get.side_effect = ConnectionError("redis down")
    redis.setex.side_effect = ConnectionError("redis down")
    es.search.return_value = {"suggest": {"product_suggest": [{"options": [{"text": "laptop"}]}]}}

    assert await service.suggest("lapt") == ["laptop"]  # served despite cache outage
