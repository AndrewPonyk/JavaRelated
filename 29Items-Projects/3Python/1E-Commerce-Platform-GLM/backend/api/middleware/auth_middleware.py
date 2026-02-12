"""
Authentication Middleware

Custom middleware for JWT authentication handling.
"""

from typing import Callable
from django.http import HttpRequest, HttpResponse
from django.conf import settings
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework.exceptions import AuthenticationFailed


class AuthenticationMiddleware:
    """
    Middleware to handle JWT authentication and refresh tokens.
    """

    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]):
        self.get_response = get_response
        self.jwt_auth = JWTAuthentication()

    def __call__(self, request: HttpRequest) -> HttpResponse:
        """
        Process request with JWT authentication.
        """
        # Skip authentication for excluded paths
        if self._should_skip_auth(request):
            return self.get_response(request)

        try:
            # Attempt to authenticate
            user_auth_tuple = self.jwt_auth.authenticate(request)
            if user_auth_tuple is not None:
                request.user, request.auth = user_auth_tuple
        except AuthenticationFailed:
            # Authentication failed, but don't block request
            # Let the view handle unauthorized access
            pass

        return self.get_response(request)

    def _should_skip_auth(self, request: HttpRequest) -> bool:
        """Check if authentication should be skipped for this path."""
        excluded_paths = [
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password/",
            "/health",
            "/metrics",
            "/api/docs",
            "/api/schema",
        ]

        return any(request.path.startswith(path) for path in excluded_paths)
