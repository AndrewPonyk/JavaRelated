"""
Celery Configuration

Celery app configuration for async task processing.
"""

from celery import Celery
from celery.schedules import crontab

from backend.core.config.settings import settings

# Create Celery app
app = Celery("ecommerce")

# Load configuration from Django settings
app.config_from_object("django.conf:settings", namespace="CELERY")

# Auto-discover tasks in all installed apps
app.autodiscover_tasks()

# Celery Beat schedule for periodic tasks
app.conf.beat_schedule = {
    # Send order reminders daily at 9 AM
    "send-order-reminders": {
        "task": "backend.tasks.email_tasks.send_order_reminders",
        "schedule": crontab(hour=9, minute=0),
    },
    # Update product recommendations every 6 hours
    "update-recommendations": {
        "task": "backend.tasks.recommendation_tasks.update_recommendations",
        "schedule": crontab(hour="*/6"),
    },
    # Sync inventory with external systems every hour
    "sync-inventory": {
        "task": "backend.tasks.inventory_tasks.sync_inventory",
        "schedule": crontab(minute=0),
    },
    # Cleanup expired sessions daily at 3 AM
    "cleanup-sessions": {
        "task": "backend.tasks.cleanup_tasks.cleanup_expired_sessions",
        "schedule": crontab(hour=3, minute=0),
    },
}


@app.task(bind=True)
def debug_task(self):
    """Debug task for testing Celery setup."""
    import logging
    logger = logging.getLogger("backend")
    logger.info(f"Request: {self.request!r}")
    return f"Request: {self.request!r}"
