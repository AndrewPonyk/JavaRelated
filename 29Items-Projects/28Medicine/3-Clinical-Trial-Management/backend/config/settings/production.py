"""Production settings — AWS GovCloud, hardened.

Required secrets are resolved from the environment (injected from AWS Secrets
Manager / SSM by the ECS task definition) and **fail fast** if missing.
"""
from __future__ import annotations

from .base import *  # noqa: F401,F403
from .base import env

DEBUG = False

# Fail fast: these MUST be provided in production.
SECRET_KEY = env("DJANGO_SECRET_KEY")  # raises ImproperlyConfigured if unset
ALLOWED_HOSTS = env("DJANGO_ALLOWED_HOSTS")
FIELD_ENCRYPTION_KEY = env("FIELD_ENCRYPTION_KEY")

# ── HTTPS / transport security ────────────────────────────────────────────
SECURE_SSL_REDIRECT = True
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True
SECURE_HSTS_SECONDS = 31_536_000  # 1 year
SECURE_HSTS_INCLUDE_SUBDOMAINS = True
SECURE_HSTS_PRELOAD = True
SECURE_CONTENT_TYPE_NOSNIFF = True
SECURE_REFERRER_POLICY = "same-origin"
X_FRAME_OPTIONS = "DENY"

# Enforce TLS to the database.
DATABASES["default"]["OPTIONS"] = {"sslmode": "require"}  # noqa: F405

# CORS locked to the known SPA origin(s).
CORS_ALLOWED_ORIGINS = env.list("CORS_ALLOWED_ORIGINS", default=[])

# Browsable API disabled in prod (JSON only).
REST_FRAMEWORK["DEFAULT_RENDERER_CLASSES"] = (  # noqa: F405
    "rest_framework.renderers.JSONRenderer",
)

# TODO: configure django-storages S3 backend (GovCloud partition, KMS SSE).
# TODO: wire Sentry/OpenTelemetry exporters (SENTRY_DSN, OTEL endpoint).
