"""Eligibility screening API.

POST a screening (subject + EHR note) → async ML screening enqueued → review the
result → POST an e-signed human decision (the authoritative outcome).
"""
from __future__ import annotations

from rest_framework.decorators import action
from rest_framework.request import Request
from rest_framework.response import Response

from apps.accounts.services import SignatureError
from apps.common.exceptions import DomainError
from apps.common.viewsets import BaseModelViewSet

from . import services
from .models import Screening
from .serializers import DecisionSerializer, ScreeningSerializer


class _SignatureFailed(DomainError):
    status_code = 403
    default_code = "SIGNATURE_FAILED"


class ScreeningViewSet(BaseModelViewSet):
    serializer_class = ScreeningSerializer

    def get_queryset(self):
        qs = Screening.objects.select_related("study", "subject").all()
        if study := self.request.query_params.get("study"):
            qs = qs.filter(study__public_id=study)
        if status_ := self.request.query_params.get("status"):
            qs = qs.filter(status=status_.upper())
        return qs

    def perform_create(self, serializer) -> None:
        data = serializer.validated_data
        screening = services.create_screening(
            study=data["study"],
            subject=data["subject"],
            note_text=data.get("note_text", ""),
            created_by=self.request.user,
        )
        serializer.instance = screening

    @action(detail=True, methods=["post"])
    def decision(self, request: Request, public_id: str | None = None) -> Response:
        """POST .../decision/ — record the authoritative human, e-signed decision."""
        payload = DecisionSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        try:
            screening = services.record_decision(
                screening=self.get_object(),
                decision=payload.validated_data["decision"],
                decided_by=request.user,
                password=payload.validated_data.get("password"),
            )
        except SignatureError as exc:
            raise _SignatureFailed(str(exc)) from exc
        return Response(self.get_serializer(screening).data)
