"""Fixed-window rate limiter semantics and failure tolerance."""

from app.core.ratelimit import MemoryRateLimiter, SafeRateLimiter


class TestMemoryRateLimiter:
    def test_allows_up_to_limit_then_blocks(self, monkeypatch):
        import app.core.ratelimit as rl

        monkeypatch.setattr(rl.time, "time", lambda: 1_000_000.0)
        limiter = MemoryRateLimiter()
        decisions = [limiter.hit("user-a", 3) for _ in range(4)]
        assert [d.allowed for d in decisions] == [True, True, True, False]
        assert decisions[-1].retry_after_seconds > 0

    def test_window_reset_restores_budget(self, monkeypatch):
        import app.core.ratelimit as rl

        clock = [1_000_000.0]
        monkeypatch.setattr(rl.time, "time", lambda: clock[0])
        limiter = MemoryRateLimiter()
        assert limiter.hit("user-a", 1).allowed
        assert not limiter.hit("user-a", 1).allowed
        clock[0] += rl.WINDOW_SECONDS  # next window
        assert limiter.hit("user-a", 1).allowed

    def test_identities_are_independent(self):
        limiter = MemoryRateLimiter()
        assert limiter.hit("user-a", 1).allowed
        assert limiter.hit("user-b", 1).allowed  # a's exhaustion doesn't touch b


class TestSafeRateLimiter:
    class _Exploding:
        def hit(self, key, limit):
            raise RuntimeError("boom")

    def test_backend_failure_degrades_to_allow(self):
        safe = SafeRateLimiter(self._Exploding())
        decision = safe.hit("anyone", 1)
        assert decision.allowed  # limiter trouble must never take down requests
