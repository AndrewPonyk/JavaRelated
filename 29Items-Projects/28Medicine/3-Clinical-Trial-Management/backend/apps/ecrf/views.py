"""eCRF API: form definitions, data capture (with edit checks), and queries."""
from __future__ import annotations

from rest_framework.decorators import action
from rest_framework.request import Request
from rest_framework.response import Response

from apps.common.viewsets import BaseModelViewSet

from .models import DataPoint, FormDefinition, Query, QueryStatus
from .serializers import DataPointSerializer, FormDefinitionSerializer, QuerySerializer


class FormDefinitionViewSet(BaseModelViewSet):
    serializer_class = FormDefinitionSerializer

    def get_queryset(self):
        qs = FormDefinition.objects.select_related("study").all()
        if study := self.request.query_params.get("study"):
            qs = qs.filter(study__public_id=study)
        return qs


class DataPointViewSet(BaseModelViewSet):
    serializer_class = DataPointSerializer

    def get_queryset(self):
        qs = DataPoint.objects.select_related("enrollment", "form").all()
        if enrollment := self.request.query_params.get("enrollment"):
            qs = qs.filter(enrollment__public_id=enrollment)
        if form := self.request.query_params.get("form"):
            qs = qs.filter(form__public_id=form)
        return qs

    @action(detail=True, methods=["post"], url_path="verify")
    def verify(self, request: Request, public_id: str | None = None) -> Response:
        """Source data verification (monitor action)."""
        dp = self.get_object()
        dp.sdv_verified = True
        dp.save(update_fields=["sdv_verified", "updated_at"])
        return Response(self.get_serializer(dp).data)


class QueryViewSet(BaseModelViewSet):
    serializer_class = QuerySerializer

    def get_queryset(self):
        qs = Query.objects.select_related("data_point").all()
        if status_ := self.request.query_params.get("status"):
            qs = qs.filter(status=status_.upper())
        return qs

    @action(detail=True, methods=["post"])
    def close(self, request: Request, public_id: str | None = None) -> Response:
        query = self.get_object()
        query.status = QueryStatus.CLOSED
        query.save(update_fields=["status", "updated_at"])
        return Response(self.get_serializer(query).data)
