"""Unit tests: token-bucket rate limiter."""

from __future__ import annotations

import pytest
from app.core.ratelimit import RateLimiter, TokenBucket


class TestTokenBucket:
    def test_burst_up_to_capacity(self):
        bucket = TokenBucket(capacity=3, refill_per_minute=6)  # 0.1/s
        assert [bucket.try_consume() for _ in range(3)] == [True, True, True]
        assert not bucket.try_consume()  # exhausted

    def test_refill_over_time(self, monkeypatch):
        bucket = TokenBucket(capacity=2, refill_per_minute=600)  # 10/s
        assert bucket.try_consume() and bucket.try_consume()
        assert not bucket.try_consume()
        # advance the monotonic clock 0.5s → ~5 tokens refilled (capped at 2)
        real_monotonic = bucket.updated
        monkeypatch.setattr("app.core.ratelimit.time.monotonic", lambda: real_monotonic + 0.5)
        assert bucket.try_consume()

    def test_refill_capped_at_capacity(self, monkeypatch):
        bucket = TokenBucket(capacity=2, refill_per_minute=600)
        bucket.try_consume()
        real = bucket.updated
        monkeypatch.setattr("app.core.ratelimit.time.monotonic", lambda: real + 60)
        bucket.try_consume()
        bucket.try_consume()
        assert not bucket.try_consume()  # still capped at 2


class TestRateLimiter:
    def test_check_raises_429_when_exhausted(self):
        limiter = RateLimiter()
        limiter._buckets.clear()
        for _ in range(20):
            limiter.check("auth", "client-a")
        with pytest.raises(Exception) as excinfo:  # HTTPException(429)
            limiter.check("auth", "client-a")
        assert excinfo.value.status_code == 429

    def test_buckets_are_isolated_per_client(self):
        limiter = RateLimiter()
        limiter._buckets.clear()
        for _ in range(20):
            limiter.check("auth", "client-a")
        limiter.check("auth", "client-b")  # separate bucket, unaffected

    def test_unknown_bucket_is_unlimited(self):
        limiter = RateLimiter()
        for _ in range(100):
            limiter.check("no-such-bucket", "x")
