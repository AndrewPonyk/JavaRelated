"""Celery application definition and beat schedule."""

from __future__ import annotations

import os

from celery import Celery
from celery.schedules import crontab

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings.development")

app = Celery("ecommerce")

# Read CELERY_* settings from Django config.
app.config_from_object("django.conf:settings", namespace="CELERY")

# Auto-discover tasks.py modules in every installed app.
app.autodiscover_tasks()

# Route heavy/independent workloads to dedicated queues so they scale alone.
app.conf.task_routes = {
    "apps.recommendations.tasks.*": {"queue": "ml"},
    "apps.orders.tasks.send_*": {"queue": "email"},
    "apps.search.tasks.*": {"queue": "default"},
}

# Periodic jobs.
app.conf.beat_schedule = {
    "retrain-recommendation-model-nightly": {
        "task": "apps.recommendations.tasks.train_model",
        "schedule": crontab(hour=3, minute=0),
    },
    "reconcile-search-index-hourly": {
        "task": "apps.search.tasks.full_reindex",
        "schedule": crontab(minute=0),
    },
    "release-expired-cart-reservations": {
        "task": "apps.inventory.tasks.release_expired_reservations",
        "schedule": crontab(minute="*/5"),
    },
}


@app.task(bind=True, ignore_result=True)
def debug_task(self) -> None:  # pragma: no cover - health/diagnostic only
    """Trivial task used to verify worker connectivity."""
    print(f"Request: {self.request!r}")
