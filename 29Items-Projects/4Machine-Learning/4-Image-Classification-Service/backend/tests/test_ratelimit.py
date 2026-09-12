"""Tests for the token-bucket rate limiter."""

from __future__ import annotations

from app.core.ratelimit import RateLimiter, TokenBucket


def test_token_bucket_allows_up_to_capacity():
    bucket = TokenBucket(capacity=3, refill_per_sec=0)
    assert [bucket.allow() for _ in range(3)] == [True, True, True]
    assert bucket.allow() is False  # exhausted, no refill


def test_rate_limiter_isolates_keys():
    limiter = RateLimiter(capacity=1, refill_per_sec=0)
    assert limiter.allow("a") is True
    assert limiter.allow("a") is False  # a exhausted
    assert limiter.allow("b") is True  # b independent


def test_rate_limiter_refills(monkeypatch):
    import app.core.ratelimit as rl

    t = {"now": 1000.0}
    monkeypatch.setattr(rl.time, "monotonic", lambda: t["now"])
    limiter = RateLimiter(capacity=1, refill_per_sec=1.0)
    assert limiter.allow("x") is True
    assert limiter.allow("x") is False
    t["now"] += 2.0  # 2 seconds pass -> refill
    assert limiter.allow("x") is True
