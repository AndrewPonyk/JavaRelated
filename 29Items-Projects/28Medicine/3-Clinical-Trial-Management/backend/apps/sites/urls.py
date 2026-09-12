from __future__ import annotations

from rest_framework.routers import DefaultRouter

from .views import DelegationLogViewSet, SiteViewSet

router = DefaultRouter()
router.register(r"sites", SiteViewSet, basename="site")
router.register(r"delegations", DelegationLogViewSet, basename="delegation")

urlpatterns = router.urls
