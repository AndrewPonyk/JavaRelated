"""Result-cache behavior: LRU, TTL, canonical keys, and failure tolerance."""

from app.services.cache import MemoryCache, SafeCache, computation_cache_key


class TestMemoryCache:
    def test_set_get(self):
        cache = MemoryCache()
        cache.set("k", {"value": 1}, ttl_seconds=60)
        assert cache.get("k") == {"value": 1}

    def test_miss(self):
        assert MemoryCache().get("absent") is None

    def test_ttl_expiry(self, monkeypatch):
        import app.services.cache as cache_module

        clock = [1000.0]
        monkeypatch.setattr(cache_module.time, "monotonic", lambda: clock[0])
        cache = MemoryCache()
        cache.set("k", {"v": 1}, ttl_seconds=10)
        clock[0] += 5
        assert cache.get("k") == {"v": 1}
        clock[0] += 6
        assert cache.get("k") is None

    def test_lru_eviction(self):
        cache = MemoryCache(max_entries=2)
        cache.set("a", {"v": 1}, 60)
        cache.set("b", {"v": 2}, 60)
        cache.get("a")  # a becomes most-recent
        cache.set("c", {"v": 3}, 60)  # evicts b
        assert cache.get("a") is not None
        assert cache.get("b") is None
        assert cache.get("c") is not None


class TestSafeCache:
    class _Exploding:
        def get(self, key):
            raise RuntimeError("boom")

        def set(self, key, value, ttl_seconds):
            raise RuntimeError("boom")

    def test_backend_failures_degrade_to_miss(self):
        safe = SafeCache(self._Exploding())
        assert safe.get("k") is None  # no raise
        safe.set("k", {"v": 1}, 60)  # no raise


def test_cache_key_is_stable_and_distinct():
    a = computation_cache_key("solve", "Eq(Pow(Symbol('x'), 2), 4)", "x")
    b = computation_cache_key("solve", "Eq(Pow(Symbol('x'), 2), 4)", "x")
    c = computation_cache_key("solve", "Eq(Pow(Symbol('x'), 2), 4)", "y")
    d = computation_cache_key("integrate", "Eq(Pow(Symbol('x'), 2), 4)", "x")
    assert a == b
    assert len({a, c, d}) == 3
