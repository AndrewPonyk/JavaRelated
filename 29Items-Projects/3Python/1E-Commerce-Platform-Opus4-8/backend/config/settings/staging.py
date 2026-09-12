"""Staging settings — production-like, smaller scale."""

from .base import *  # noqa: F401,F403

DEBUG = False

# Hosts and CORS origins come strictly from the environment.
ALLOWED_HOSTS = env.list("DJANGO_ALLOWED_HOSTS")  # noqa: F405
CORS_ALLOWED_ORIGINS = env.list("CORS_ALLOWED_ORIGINS", default=[])  # noqa: F405

# Security hardening.
SECURE_SSL_REDIRECT = True
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True
SECURE_HSTS_SECONDS = 3600
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")

# Sentry / observability would be initialised here (see TODO in production.py).
