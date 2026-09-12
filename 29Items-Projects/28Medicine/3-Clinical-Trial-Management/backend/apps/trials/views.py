"""Trials REST API (Deliverable 4.2 — Backend Endpoint).

Demonstrates: CRUD viewsets, service-layer integration (state transitions go
through ``services`` rather than mutating in the view), input validation via
serializers, and custom actions returning derived data.
"""
from __future__ import annotations

from rest_framework.decorators import action
from rest_framework.request import Request
from rest_framework.response import Response

from apps.common.exceptions import ConflictError
from apps.common.viewsets import BaseModelViewSet

from . import services
from .models import Arm, EligibilityCriterion, Protocol, Study
from .serializers import (
    ArmWriteSerializer,
    EligibilityCriterionSerializer,
    ProtocolSerializer,
    StudySerializer,
)


class ProtocolViewSet(BaseModelViewSet):
    serializer_class = ProtocolSerializer

    def get_queryset(self):
        qs = Protocol.objects.prefetch_related("criteria").all()
        if code := self.request.query_params.get("code"):
            qs = qs.filter(code__iexact=code)
        return qs


class EligibilityCriterionViewSet(BaseModelViewSet):
    serializer_class = EligibilityCriterionSerializer

    def get_queryset(self):
        qs = EligibilityCriterion.objects.select_related("protocol").all()
        if protocol := self.request.query_params.get("protocol"):
            qs = qs.filter(protocol__public_id=protocol)
        return qs


class ArmViewSet(BaseModelViewSet):
    serializer_class = ArmWriteSerializer

    def get_queryset(self):
        qs = Arm.objects.select_related("study").all()
        if study := self.request.query_params.get("study"):
            qs = qs.filter(study__public_id=study)
        return qs


class StudyViewSet(BaseModelViewSet):
    """CRUD for studies + ``/open`` and ``/progress`` actions.

    ``select_related`` / ``prefetch_related`` avoid N+1 queries (TECH-NOTES §3.6).
    """

    serializer_class = StudySerializer

    def get_queryset(self):
        qs = (
            Study.objects.select_related("protocol")
            .prefetch_related("arms", "visit_templates")
            .all()
        )
        if status_filter := self.request.query_params.get("status"):
            qs = qs.filter(status=status_filter.upper())
        return qs

    @action(detail=True, methods=["post"])
    def open(self, request: Request, public_id: str | None = None) -> Response:
        """POST .../open/ — open a study to enrollment (guarded transition)."""
        study = self.get_object()
        try:
            services.open_study(study)
        except services.StudyStateError as exc:
            raise ConflictError(exc.messages[0] if exc.messages else str(exc)) from exc
        return Response(self.get_serializer(study).data)

    @action(detail=True, methods=["post"])
    def close(self, request: Request, public_id: str | None = None) -> Response:
        study = self.get_object()
        try:
            services.close_study(study)
        except services.StudyStateError as exc:
            raise ConflictError(exc.messages[0] if exc.messages else str(exc)) from exc
        return Response(self.get_serializer(study).data)

    @action(detail=True, methods=["get"])
    def progress(self, request: Request, public_id: str | None = None) -> Response:
        """GET .../progress/ — enrollment progress via the service layer."""
        return Response(services.enrollment_progress(self.get_object()))
