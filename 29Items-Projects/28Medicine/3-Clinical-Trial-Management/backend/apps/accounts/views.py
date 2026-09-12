"""Accounts API: current-user, user administration, and JWT login.

Login layers an audit LOGIN event onto SimpleJWT's token-obtain view.
"""
from __future__ import annotations

from drf_spectacular.utils import extend_schema
from rest_framework import mixins, permissions, viewsets
from rest_framework.decorators import api_view, permission_classes
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework_simplejwt.views import TokenObtainPairView

from apps.audit.middleware import get_audit_context
from apps.audit.models import AuditAction, AuditEvent

from .models import Role, User
from .serializers import UserCreateSerializer, UserSerializer


@extend_schema(responses=UserSerializer)
@api_view(["GET"])
@permission_classes([permissions.IsAuthenticated])
def me(request: Request) -> Response:
    """GET /api/v1/auth/me/ — the authenticated user's profile."""
    return Response(UserSerializer(request.user).data)


class AuditedTokenObtainPairView(TokenObtainPairView):
    """JWT login that records a LOGIN audit event on success."""

    def post(self, request, *args, **kwargs):
        response = super().post(request, *args, **kwargs)
        if response.status_code == 200:
            username = request.data.get("username", "")
            user = User.objects.filter(username=username).first()
            ctx = get_audit_context()
            last = AuditEvent.objects.order_by("-id").values_list("row_hash", flat=True).first()
            AuditEvent.objects.create(
                actor=user,
                actor_label=str(user) if user else username,
                action=AuditAction.LOGIN,
                correlation_id=ctx.correlation_id,
                source_ip=ctx.source_ip,
                entity_type="accounts.User",
                entity_id=str(user.pk) if user else "",
                reason="Successful authentication",
                prev_hash=last or "",
            )
        return response


class UserViewSet(
    mixins.ListModelMixin,
    mixins.RetrieveModelMixin,
    mixins.CreateModelMixin,
    viewsets.GenericViewSet,
):
    """User administration — staff/sponsor only."""

    queryset = User.objects.all().order_by("username")
    permission_classes = [permissions.IsAdminUser]

    def get_serializer_class(self):
        return UserCreateSerializer if self.action == "create" else UserSerializer

    def get_queryset(self):
        qs = super().get_queryset()
        role = self.request.query_params.get("role")
        if role in Role.values:
            qs = qs.filter(role=role)
        return qs
