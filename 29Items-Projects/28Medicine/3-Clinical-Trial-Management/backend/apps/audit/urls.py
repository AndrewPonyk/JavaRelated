from __future__ import annotations

from rest_framework.routers import DefaultRouter

from .views import AuditEventViewSet

router = DefaultRouter()
router.register(r"events", AuditEventViewSet, basename="audit-event")

urlpatterns = router.urls
