from __future__ import annotations

from rest_framework.routers import DefaultRouter

from .views import DeviationViewSet, EnrollmentViewSet, VisitViewSet

router = DefaultRouter()
router.register(r"enrollments", EnrollmentViewSet, basename="enrollment")
router.register(r"visits", VisitViewSet, basename="visit")
router.register(r"deviations", DeviationViewSet, basename="deviation")

urlpatterns = router.urls
