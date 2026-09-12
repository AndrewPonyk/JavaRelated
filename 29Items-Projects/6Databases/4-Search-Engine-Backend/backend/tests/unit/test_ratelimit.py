"""Sliding-window limiter behavior (time injected — no sleeps)."""

from app.core.ratelimit import SlidingWindowLimiter, client_ip


def test_allows_up_to_limit_then_blocks() -> None:
    limiter = SlidingWindowLimiter(max_requests=3, window_s=10.0)
    assert all(limiter.allow("ip", now=t) for t in (0.0, 1.0, 2.0))
    assert limiter.allow("ip", now=3.0) is False


def test_window_slides_and_recovers() -> None:
    limiter = SlidingWindowLimiter(max_requests=2, window_s=10.0)
    assert limiter.allow("ip", now=0.0)
    assert limiter.allow("ip", now=1.0)
    assert limiter.allow("ip", now=5.0) is False
    assert limiter.allow("ip", now=10.5) is True  # first hit expired


def test_clients_are_isolated() -> None:
    limiter = SlidingWindowLimiter(max_requests=1, window_s=10.0)
    assert limiter.allow("a", now=0.0)
    assert limiter.allow("b", now=0.0)
    assert limiter.allow("a", now=1.0) is False


def test_eviction_bounds_memory() -> None:
    limiter = SlidingWindowLimiter(max_requests=1, window_s=10.0, max_clients=4)
    for i in range(10):
        limiter.allow(f"client-{i}", now=float(i))
    assert len(limiter._hits) <= 4  # noqa: SLF001 — asserting the memory bound


def test_client_ip_prefers_first_forwarded_hop() -> None:
    assert client_ip({"x-forwarded-for": "1.2.3.4, 10.0.0.1"}, "9.9.9.9") == "1.2.3.4"
    assert client_ip({}, "9.9.9.9") == "9.9.9.9"
