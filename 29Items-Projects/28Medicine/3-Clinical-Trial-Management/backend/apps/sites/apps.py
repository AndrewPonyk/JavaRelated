from django.apps import AppConfig


class SitesConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.sites"
    label = "trial_sites"  # avoid clash with django.contrib.sites naming
