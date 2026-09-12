"""Test settings — fast, hermetic, deterministic (TECH-NOTES §3.2).

Uses in-memory SQLite so the suite runs with no external services (CI spins up
Postgres for the integration job, but unit tests must run anywhere).
"""
from __future__ import annotations

from .base import *  # noqa: F401,F403

DEBUG = False

# Hermetic DB — no Postgres required.
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": ":memory:",
    }
}

# Fast password hashing in tests.
PASSWORD_HASHERS = ["django.contrib.auth.hashers.MD5PasswordHasher"]

# Run Celery tasks inline so task wiring is exercised without a broker.
CELERY_TASK_ALWAYS_EAGER = True
CELERY_TASK_EAGER_PROPAGATES = True
CELERY_BROKER_URL = "memory://"
CELERY_RESULT_BACKEND = "cache+memory://"

# Deterministic encryption key for PHI field tests (Fernet, base64 32 bytes).
FIELD_ENCRYPTION_KEY = "dGVzdC1rZXktMzItYnl0ZXMtZm9yLWZlcm5ldC1lbmM9"

# Quieter logging during tests.
LOGGING = {"version": 1, "disable_existing_loggers": False}
