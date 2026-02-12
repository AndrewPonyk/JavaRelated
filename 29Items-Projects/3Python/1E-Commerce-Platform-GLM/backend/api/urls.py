"""
API URL Configuration

URL routing for the REST API.
"""

from django.urls import path, include
from rest_framework.routers import DefaultRouter
from drf_spectacular.views import (
    SpectacularAPIView,
    SpectacularRedocView,
    SpectacularSwaggerView,
)

from backend.api.controllers import (
    product_controller,
    cart_controller,
    order_controller,
    auth_controller,
    search_controller,
)
from backend.api.controllers.health_controller import (
    HealthCheckView,
    LivenessProbeView,
    ReadinessProbeView,
)

# Create router
router = DefaultRouter()

# Register viewsets
router.register(r"products", product_controller.ProductViewSet, basename="product")
router.register(r"categories", product_controller.CategoryViewSet, basename="category")
router.register(r"cart", cart_controller.CartViewSet, basename="cart")
router.register(r"orders", order_controller.OrderViewSet, basename="order")
router.register(r"auth", auth_controller.AuthViewSet, basename="auth")
router.register(r"search", search_controller.SearchViewSet, basename="search")

app_name = "api"

urlpatterns = [
    # Health check endpoints (no authentication required)
    path("health/", HealthCheckView.as_view(), name="health-check"),
    path("healthz/", LivenessProbeView.as_view(), name="liveness-probe"),
    path("readyz/", ReadinessProbeView.as_view(), name="readiness-probe"),

    # API endpoints
    path("v1/", include(router.urls)),

    # Schema and documentation
    path("schema/", SpectacularAPIView.as_view(), name="schema"),
    path("docs/", SpectacularSwaggerView.as_view(url_name="schema"), name="swagger-ui"),
    path("redoc/", SpectacularRedocView.as_view(url_name="schema"), name="redoc"),
]
