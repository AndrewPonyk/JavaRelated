"""Tests for the TTL cache."""

from app.core.cache import TTLCache


def test_set_and_get():
    cache: TTLCache[str] = TTLCache(ttl_seconds=10)
    cache.set("k", "v")
    assert cache.get("k") == "v"


def test_missing_key_returns_none():
    assert TTLCache(ttl_seconds=10).get("absent") is None


def test_expired_entry_returns_none():
    cache: TTLCache[str] = TTLCache(ttl_seconds=-1)  # already expired on set
    cache.set("k", "v")
    assert cache.get("k") is None


def test_maxsize_eviction():
    cache: TTLCache[int] = TTLCache(ttl_seconds=100, maxsize=2)
    cache.set("a", 1)
    cache.set("b", 2)
    cache.set("c", 3)
    assert len(cache) <= 2


def test_clear():
    cache: TTLCache[int] = TTLCache(ttl_seconds=100)
    cache.set("a", 1)
    cache.clear()
    assert len(cache) == 0
