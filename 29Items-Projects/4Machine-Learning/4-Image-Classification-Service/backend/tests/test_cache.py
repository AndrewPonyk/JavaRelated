"""Tests for the read-through cache, including graceful degradation."""

from __future__ import annotations

from app.models.schemas import LabelPrediction
from app.services.cache import CacheService


class FakeRedis:
    def __init__(self):
        self.store: dict[str, bytes] = {}

    def get(self, key):
        return self.store.get(key)

    def setex(self, key, ttl, value):  # noqa: ARG002 - ttl unused in fake
        self.store[key] = value


def test_cache_disabled_returns_none():
    cache = CacheService(client=None, ttl_seconds=60, model_version="v1")
    assert cache.get("abc") is None
    cache.set("abc", [LabelPrediction(name="x", score=0.9)])  # no-op, no error


def test_cache_roundtrip():
    cache = CacheService(client=FakeRedis(), ttl_seconds=60, model_version="v1")
    preds = [LabelPrediction(name="electronics", score=0.97)]
    cache.set("hash1", preds)
    got = cache.get("hash1")
    assert got is not None
    assert got[0].name == "electronics"
    assert got[0].score == 0.97


def test_cache_key_namespaced_by_model_version():
    redis = FakeRedis()
    cache_v1 = CacheService(client=redis, ttl_seconds=60, model_version="v1")
    cache_v2 = CacheService(client=redis, ttl_seconds=60, model_version="v2")
    cache_v1.set("h", [LabelPrediction(name="a", score=0.5)])
    # v2 must not see v1's cached value (different namespace).
    assert cache_v2.get("h") is None


class BrokenRedis:
    def get(self, key):
        raise ConnectionError("redis down")

    def setex(self, key, ttl, value):
        raise ConnectionError("redis down")


def test_cache_degrades_on_error():
    cache = CacheService(client=BrokenRedis(), ttl_seconds=60, model_version="v1")
    # Errors are swallowed: get -> None, set -> no raise.
    assert cache.get("h") is None
    cache.set("h", [LabelPrediction(name="a", score=0.5)])
