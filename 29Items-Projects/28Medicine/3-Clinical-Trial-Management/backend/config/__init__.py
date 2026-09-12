"""Django project package for the Clinical Trial Management System.

Ensures the Celery app is imported when Django starts so that shared_task
decorators across the apps use this app instance.
"""
from __future__ import annotations

from .celery import app as celery_app

__all__ = ("celery_app",)
