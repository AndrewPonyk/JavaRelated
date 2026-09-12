"""Test settings — fast, hermetic, no external services required.

SQLite in-memory DB, eager Celery (tasks run inline), local-memory cache, and
a console email backend. The Elasticsearch client is mocked in the tests that
exercise search, so no ES server is needed.
"""

from .base import *  # noqa: F401,F403

DEBUG = False
ALLOWED_HOSTS = ["*"]

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": ":memory:",
    }
}

# Run Celery tasks synchronously and surface exceptions.
CELERY_TASK_ALWAYS_EAGER = True
CELERY_TASK_EAGER_PROPAGATES = True

CACHES = {
    "default": {
        "BACKEND": "django.core.cache.backends.locmem.LocMemCache",
    }
}

EMAIL_BACKEND = "django.core.mail.backends.locmem.EmailBackend"

# Deterministic, offline payment gateway.
PAYMENT_GATEWAY = "fake"

# No Elasticsearch server in the test environment.
SEARCH_INDEXING_ENABLED = False

# Speed up password hashing in tests.
PASSWORD_HASHERS = ["django.contrib.auth.hashers.MD5PasswordHasher"]

# Disable rate limiting so the suite is deterministic (rate=None => no throttle).
REST_FRAMEWORK = {  # noqa: F405
    **REST_FRAMEWORK,  # noqa: F405
    "DEFAULT_THROTTLE_RATES": {"anon": None, "user": None, "auth": None},
}

# Quieter logs during tests.
LOGGING["root"]["level"] = "WARNING"  # noqa: F405
