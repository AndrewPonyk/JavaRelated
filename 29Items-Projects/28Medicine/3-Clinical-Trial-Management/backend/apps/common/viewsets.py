"""Shared base viewset.

Centralizes the conventions every API resource follows: UUID lookups (never
expose sequential PKs), RBAC write-blocking for read-only roles, and stamping
``created_by`` on creation. The authoritative who/what/when still comes from the
audit trail (apps.audit), this is just convenience authorship.
"""
from __future__ import annotations

from rest_framework import viewsets
from rest_framework.permissions import IsAuthenticated

from .permissions import IsNotReadOnlyRole


class BaseModelViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAuthenticated, IsNotReadOnlyRole]
    lookup_field = "public_id"
    lookup_url_kwarg = "public_id"

    def perform_create(self, serializer) -> None:
        serializer.save(created_by=self.request.user)
