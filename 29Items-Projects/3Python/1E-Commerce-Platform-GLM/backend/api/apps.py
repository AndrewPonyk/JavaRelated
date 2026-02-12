"""
API Application Configuration

Django app configuration for the REST API.
"""

from django.apps import AppConfig


class ApiConfig(AppConfig):
    """Configuration for the API application."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "backend.api"
    verbose_name = "API"

    def ready(self):
        """
        Application initialization code.

        Import signals and other startup code here.
        """
        # Import signals
        # import backend.api.signals
        pass
