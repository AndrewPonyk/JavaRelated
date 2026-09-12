"""Enrollment API — workflow transitions exposed as guarded actions."""
from __future__ import annotations

from rest_framework import mixins, viewsets
from rest_framework.decorators import action
from rest_framework.request import Request
from rest_framework.response import Response

from apps.accounts.services import SignatureError
from apps.common.exceptions import DomainError
from apps.common.viewsets import BaseModelViewSet

from . import services
from .models import Deviation, Enrollment, Visit, VisitStatus
from .serializers import (
    ActivateSerializer,
    DeviationSerializer,
    EnrollmentSerializer,
    SignActionSerializer,
    VisitSerializer,
    WithdrawSerializer,
)


class _SignatureFailed(DomainError):
    status_code = 403
    default_code = "SIGNATURE_FAILED"


class EnrollmentViewSet(BaseModelViewSet):
    serializer_class = EnrollmentSerializer

    def get_queryset(self):
        qs = Enrollment.objects.select_related("study", "subject", "arm").all()
        if study := self.request.query_params.get("study"):
            qs = qs.filter(study__public_id=study)
        if status_ := self.request.query_params.get("status"):
            qs = qs.filter(status=status_.upper())
        return qs

    def perform_create(self, serializer) -> None:
        data = serializer.validated_data
        enrollment = services.create_enrollment(
            study=data["study"], subject=data["subject"], created_by=self.request.user
        )
        serializer.instance = enrollment

    @action(detail=True, methods=["post"])
    def consent(self, request: Request, public_id: str | None = None) -> Response:
        payload = SignActionSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        try:
            enrollment = services.give_consent(
                self.get_object(),
                signer=request.user,
                password=payload.validated_data.get("password"),
            )
        except SignatureError as exc:
            raise _SignatureFailed(str(exc)) from exc
        return Response(self.get_serializer(enrollment).data)

    @action(detail=True, methods=["post"])
    def randomize(self, request: Request, public_id: str | None = None) -> Response:
        payload = SignActionSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        try:
            enrollment = services.randomize(
                self.get_object(),
                signer=request.user,
                password=payload.validated_data.get("password"),
            )
        except SignatureError as exc:
            raise _SignatureFailed(str(exc)) from exc
        return Response(self.get_serializer(enrollment).data)

    @action(detail=True, methods=["post"])
    def activate(self, request: Request, public_id: str | None = None) -> Response:
        payload = ActivateSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        enrollment = services.activate(
            self.get_object(), baseline_date=payload.validated_data.get("baseline_date")
        )
        return Response(self.get_serializer(enrollment).data)

    @action(detail=True, methods=["post"])
    def withdraw(self, request: Request, public_id: str | None = None) -> Response:
        payload = WithdrawSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        enrollment = services.withdraw(self.get_object(), reason=payload.validated_data["reason"])
        return Response(self.get_serializer(enrollment).data)

    @action(detail=True, methods=["post"], url_path="screen-fail")
    def screen_fail(self, request: Request, public_id: str | None = None) -> Response:
        payload = WithdrawSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        enrollment = services.screen_fail(
            self.get_object(), reason=payload.validated_data["reason"]
        )
        return Response(self.get_serializer(enrollment).data)

    @action(detail=True, methods=["get"])
    def visits(self, request: Request, public_id: str | None = None) -> Response:
        visits = self.get_object().visits.select_related("template").all()
        return Response(VisitSerializer(visits, many=True).data)


class VisitViewSet(
    mixins.RetrieveModelMixin,
    mixins.ListModelMixin,
    mixins.UpdateModelMixin,
    viewsets.GenericViewSet,
):
    """Record actual visits (mark completed)."""

    serializer_class = VisitSerializer
    lookup_field = "public_id"
    lookup_url_kwarg = "public_id"

    def get_queryset(self):
        qs = Visit.objects.select_related("template", "enrollment").all()
        if enrollment := self.request.query_params.get("enrollment"):
            qs = qs.filter(enrollment__public_id=enrollment)
        return qs

    def perform_update(self, serializer) -> None:
        visit = serializer.save()
        if visit.actual_date and visit.status != VisitStatus.COMPLETED:
            visit.status = VisitStatus.COMPLETED
            visit.save(update_fields=["status", "updated_at"])


class DeviationViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    serializer_class = DeviationSerializer
    lookup_field = "public_id"
    lookup_url_kwarg = "public_id"

    def get_queryset(self):
        qs = Deviation.objects.select_related("enrollment", "template").all()
        if resolved := self.request.query_params.get("resolved"):
            qs = qs.filter(resolved=resolved.lower() == "true")
        return qs
