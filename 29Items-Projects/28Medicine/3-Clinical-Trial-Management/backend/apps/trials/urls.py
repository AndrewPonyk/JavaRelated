"""Router for the trials API."""
from __future__ import annotations

from rest_framework.routers import DefaultRouter

from .views import (
    ArmViewSet,
    EligibilityCriterionViewSet,
    ProtocolViewSet,
    StudyViewSet,
)

router = DefaultRouter()
router.register(r"protocols", ProtocolViewSet, basename="protocol")
router.register(r"criteria", EligibilityCriterionViewSet, basename="criterion")
router.register(r"arms", ArmViewSet, basename="arm")
router.register(r"studies", StudyViewSet, basename="study")

urlpatterns = router.urls
