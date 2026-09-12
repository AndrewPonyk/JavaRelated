"""Read-only audit trail API. Writes are impossible (append-only by design)."""
from __future__ import annotations

from rest_framework import permissions, viewsets

from .models import AuditEvent
from .serializers import AuditEventSerializer


class AuditEventViewSet(viewsets.ReadOnlyModelViewSet):
    """List/retrieve audit events. Filterable by entity for inspections."""

    serializer_class = AuditEventSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        qs = AuditEvent.objects.all().order_by("-id")
        params = self.request.query_params
        if entity_type := params.get("entity_type"):
            qs = qs.filter(entity_type=entity_type)
        if entity_id := params.get("entity_id"):
            qs = qs.filter(entity_id=entity_id)
        if action := params.get("action"):
            qs = qs.filter(action=action.upper())
        return qs
