"""Local development settings."""
from __future__ import annotations

from .base import *  # noqa: F401,F403
from .base import REST_FRAMEWORK

DEBUG = True
ALLOWED_HOSTS = ["localhost", "127.0.0.1", "0.0.0.0"]

# Browsable API is convenient locally.
REST_FRAMEWORK = {
    **REST_FRAMEWORK,
    "DEFAULT_RENDERER_CLASSES": (
        "rest_framework.renderers.JSONRenderer",
        "rest_framework.renderers.BrowsableAPIRenderer",
    ),
}

# Run Celery tasks synchronously unless a broker is available — handy for tests/dev.
# CELERY_TASK_ALWAYS_EAGER = True
