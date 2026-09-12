"""Vendor API — public browse + authenticated self-service onboarding."""

from __future__ import annotations

from rest_framework import status, viewsets
from rest_framework.decorators import action
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response

from apps.accounts.models import User

from .models import Vendor
from .serializers import VendorSerializer


class VendorViewSet(viewsets.ModelViewSet):
    """`/api/v1/vendors/` — list/detail are public; writes require auth."""

    serializer_class = VendorSerializer
    lookup_field = "slug"

    def get_permissions(self):
        if self.action in ("list", "retrieve"):
            return [AllowAny()]
        return [IsAuthenticated()]

    def get_queryset(self):
        # Public browse shows approved vendors only.
        if self.action in ("list", "retrieve"):
            return Vendor.objects.filter(status=Vendor.Status.APPROVED)
        return Vendor.objects.all()

    def perform_create(self, serializer) -> None:
        # A user gets exactly one vendor profile; promote them to the vendor role.
        user = self.request.user
        vendor = serializer.save(owner=user)
        if user.role != User.Role.STAFF:
            user.role = User.Role.VENDOR
            user.save(update_fields=["role"])
        return vendor

    def create(self, request: Request, *args, **kwargs) -> Response:
        if Vendor.objects.filter(owner=request.user).exists():
            return Response(
                {"detail": "You already have a vendor profile."},
                status=status.HTTP_409_CONFLICT,
            )
        return super().create(request, *args, **kwargs)

    @action(detail=False, methods=["get"], permission_classes=[IsAuthenticated])
    def me(self, request: Request) -> Response:
        """GET /api/v1/vendors/me/ — the caller's own vendor profile."""
        vendor = Vendor.objects.filter(owner=request.user).first()
        if vendor is None:
            return Response({"detail": "No vendor profile."}, status=status.HTTP_404_NOT_FOUND)
        return Response(VendorSerializer(vendor).data)
