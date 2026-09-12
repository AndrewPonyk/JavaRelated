from django.apps import AppConfig


class AuditConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.audit"

    def ready(self) -> None:
        # Connect audit signal receivers on startup.
        from . import signals  # noqa: F401
