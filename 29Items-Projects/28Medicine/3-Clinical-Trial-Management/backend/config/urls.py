"""Root URL configuration — API is versioned under /api/v1/."""
from __future__ import annotations

from django.contrib import admin
from django.db import connection
from django.http import JsonResponse
from django.urls import include, path
from drf_spectacular.views import (  # type: ignore[import-untyped]
    SpectacularAPIView,
    SpectacularSwaggerView,
)


def healthz(_request) -> JsonResponse:
    """Liveness probe (no DB hit) — is the process up?"""
    return JsonResponse({"status": "ok"})


def readyz(_request) -> JsonResponse:
    """Readiness probe — can we actually serve traffic (DB reachable)?"""
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
    except Exception:  # noqa: BLE001 — any DB failure means not ready
        return JsonResponse({"status": "unavailable", "database": "down"}, status=503)
    return JsonResponse({"status": "ok", "database": "up"})


api_v1_patterns = [
    path("auth/", include("apps.accounts.urls")),
    path("", include("apps.trials.urls")),
    path("", include("apps.sites.urls")),
    path("", include("apps.patients.urls")),
    path("", include("apps.enrollment.urls")),
    path("", include("apps.ecrf.urls")),
    path("", include("apps.eligibility.urls")),
    path("", include("apps.notifications.urls")),
    path("audit/", include("apps.audit.urls")),
]

urlpatterns = [
    path("healthz/", healthz, name="healthz"),
    path("readyz/", readyz, name="readyz"),
    path("admin/", admin.site.urls),
    path("api/v1/", include((api_v1_patterns, "api"), namespace="v1")),
    # OpenAPI schema + docs (drives the generated TS client — see TECH-NOTES §3.6).
    path("api/schema/", SpectacularAPIView.as_view(), name="schema"),
    path(
        "api/docs/",
        SpectacularSwaggerView.as_view(url_name="schema"),
        name="swagger-ui",
    ),
]
