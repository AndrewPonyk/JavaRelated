from __future__ import annotations

from rest_framework.routers import DefaultRouter

from .views import DataPointViewSet, FormDefinitionViewSet, QueryViewSet

router = DefaultRouter()
router.register(r"forms", FormDefinitionViewSet, basename="form")
router.register(r"datapoints", DataPointViewSet, basename="datapoint")
router.register(r"queries", QueryViewSet, basename="query")

urlpatterns = router.urls
