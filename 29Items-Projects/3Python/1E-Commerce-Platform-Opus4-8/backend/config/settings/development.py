"""Development settings — local Docker Compose stack."""

from .base import *  # noqa: F401,F403

DEBUG = True
ALLOWED_HOSTS = ["*"]

# Allow the Vite dev server to talk to the API.
CORS_ALLOW_ALL_ORIGINS = True

# Run Celery tasks synchronously in process for easier local debugging.
CELERY_TASK_ALWAYS_EAGER = env.bool("CELERY_TASK_ALWAYS_EAGER", default=False)  # noqa: F405

# Verbose SQL logging is opt-in to avoid noise.
if env.bool("LOG_SQL", default=False):  # noqa: F405
    LOGGING["loggers"] = {  # noqa: F405
        "django.db.backends": {"handlers": ["console"], "level": "DEBUG"},
    }
