"""
Rate Limit Middleware

Custom middleware for API rate limiting using Redis.
"""

import time
from typing import Callable
from django.http import HttpRequest, HttpResponse
from django.core.cache import cache
from django.conf import settings
from rest_framework import status


class RateLimitMiddleware:
    """
    Rate limiting middleware using Redis sliding window.
    """

    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]):
        self.get_response = get_response
        self.default_rate = getattr(settings, "RATE_LIMIT_PER_MINUTE", 60)
        self.default_burst = getattr(settings, "RATE_LIMIT_BURST", 100)

    def __call__(self, request: HttpRequest) -> HttpResponse:
        """
        Process request with rate limiting.
        """
        # Skip rate limiting for admin and staff
        if request.user.is_authenticated and request.user.is_staff:
            return self.get_response(request)

        # Get client identifier
        client_id = self._get_client_id(request)

        # Check rate limit
        if not self._check_rate_limit(client_id):
            return self._rate_limit_exceeded_response(request)

        return self.get_response(request)

    def _get_client_id(self, request: HttpRequest) -> str:
        """Get unique identifier for the client."""
        # Use user ID if authenticated, otherwise IP address
        if request.user.is_authenticated:
            return f"user:{request.user.id}"
        return f"ip:{self._get_client_ip(request)}"

    def _get_client_ip(self, request: HttpRequest) -> str:
        """Get client IP address from request."""
        x_forwarded_for = request.META.get("HTTP_X_FORWARDED_FOR")
        if x_forwarded_for:
            ip = x_forwarded_for.split(",")[0].strip()
        else:
            ip = request.META.get("REMOTE_ADDR", "")
        return ip

    def _check_rate_limit(self, client_id: str) -> bool:
        """
        Check if client has exceeded rate limit.

        Uses sliding window algorithm.
        """
        current_time = time.time()
        window_start = current_time - 60  # 1 minute window

        # Get current request timestamps
        cache_key = f"ratelimit:{client_id}"
        requests = cache.get(cache_key, [])

        # Remove old requests outside the window
        requests = [req_time for req_time in requests if req_time > window_start]

        # Check if limit exceeded
        if len(requests) >= self.default_rate:
            return False

        # Add current request
        requests.append(current_time)
        cache.set(cache_key, requests, timeout=60)

        return True

    def _rate_limit_exceeded_response(self, request: HttpRequest) -> HttpResponse:
        """Return response when rate limit is exceeded."""
        response = HttpResponse(
            '{"error": {"code": "RATE_LIMIT_EXCEEDED", "message": "Too many requests. Please try again later."}}',
            content_type="application/json",
            status=status.HTTP_429_TOO_MANY_REQUESTS,
        )
        response["Retry-After"] = "60"
        response["X-RateLimit-Limit"] = str(self.default_rate)
        response["X-RateLimit-Window"] = "60"
        return response


# TODO: Add per-endpoint rate limits
# TODO: Add rate limit headers for all responses
