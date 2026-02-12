"""
Core Application Configuration

Django app configuration for the core application.
"""

from django.apps import AppConfig


class CoreConfig(AppConfig):
    """Configuration for the core application."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "backend.core"
    verbose_name = "Core"

    def ready(self):
        """
        Application initialization code.

        Import signals and other startup code here.
        """
        pass
