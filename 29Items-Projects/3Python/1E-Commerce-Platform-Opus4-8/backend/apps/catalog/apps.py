from django.apps import AppConfig


class CatalogConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.catalog"
    verbose_name = "Catalog"

    def ready(self) -> None:
        # Import signal handlers (e.g. keep Elasticsearch in sync on save).
        from . import signals  # noqa: F401
