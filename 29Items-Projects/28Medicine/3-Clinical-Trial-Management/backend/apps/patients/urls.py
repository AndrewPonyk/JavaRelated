from __future__ import annotations

from rest_framework.routers import DefaultRouter

from .views import SubjectViewSet

router = DefaultRouter()
router.register(r"subjects", SubjectViewSet, basename="subject")

urlpatterns = router.urls
