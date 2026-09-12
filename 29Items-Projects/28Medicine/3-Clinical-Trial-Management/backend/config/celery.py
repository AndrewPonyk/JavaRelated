"""Celery application + Beat schedule for the CTMS.

Workers consume from dedicated queues so a slow ML backlog cannot starve
time-sensitive reminders. See ARCHITECTURE.md §2.4.
"""
from __future__ import annotations

import os

from celery import Celery
from celery.schedules import crontab

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings.development")

app = Celery("ctms")

# Read CELERY_* settings from Django settings.
app.config_from_object("django.conf:settings", namespace="CELERY")

# Discover tasks.py modules across installed apps.
app.autodiscover_tasks()

# Route work onto isolated queues (see TECH-NOTES §3.6 — queue isolation).
app.conf.task_routes = {
    "apps.eligibility.tasks.*": {"queue": "ml"},
    "apps.notifications.tasks.*": {"queue": "notifications"},
    "apps.ecrf.tasks.export_*": {"queue": "reports"},
}

# Reliability defaults: tasks are idempotent, so ack late + reject on worker loss.
app.conf.task_acks_late = True
app.conf.task_reject_on_worker_lost = True
app.conf.task_default_queue = "default"

# Scheduled (Beat) jobs. Run a SINGLE beat instance to avoid double-firing.
app.conf.beat_schedule = {
    "detect-protocol-deviations": {
        "task": "apps.trials.tasks.detect_protocol_deviations",
        "schedule": crontab(minute=0),  # hourly
    },
    "send-visit-reminders": {
        "task": "apps.notifications.tasks.send_visit_reminders",
        "schedule": crontab(hour=7, minute=0),  # daily 07:00
    },
    "rescreen-pending-candidates": {
        "task": "apps.eligibility.tasks.rescreen_pending_candidates",
        "schedule": crontab(hour="*/6", minute=15),  # every 6h
    },
    "verify-audit-trail-integrity": {
        "task": "apps.audit.tasks.verify_audit_integrity",
        "schedule": crontab(hour=2, minute=30),  # nightly
    },
}
