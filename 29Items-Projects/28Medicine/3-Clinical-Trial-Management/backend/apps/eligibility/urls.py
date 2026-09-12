from __future__ import annotations

from rest_framework.routers import DefaultRouter

from .views import ScreeningViewSet

router = DefaultRouter()
router.register(r"screenings", ScreeningViewSet, basename="screening")

urlpatterns = router.urls
