"""Root URL configuration. Versioned API under /api/v1/."""

from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path
from drf_spectacular.views import (
    SpectacularAPIView,
    SpectacularSwaggerView,
)


def healthz(_request) -> JsonResponse:
    """Liveness probe used by the ECS/ALB health check."""
    return JsonResponse({"status": "ok"})


api_v1 = [
    path("auth/", include("apps.accounts.urls")),
    path("catalog/", include("apps.catalog.urls")),
    path("cart/", include("apps.cart.urls")),
    path("orders/", include("apps.orders.urls")),
    path("search/", include("apps.search.urls")),
    path("recommendations/", include("apps.recommendations.urls")),
    path("vendors/", include("apps.vendors.urls")),
]

urlpatterns = [
    path("admin/", admin.site.urls),
    path("healthz", healthz, name="healthz"),
    # OpenAPI 3 schema + interactive Swagger UI.
    path("api/schema/", SpectacularAPIView.as_view(), name="schema"),
    path(
        "api/docs/",
        SpectacularSwaggerView.as_view(url_name="schema"),
        name="swagger-ui",
    ),
    path("api/v1/", include((api_v1, "api"), namespace="v1")),
]
