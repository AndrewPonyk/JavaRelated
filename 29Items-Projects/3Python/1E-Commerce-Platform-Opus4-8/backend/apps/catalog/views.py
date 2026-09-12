"""Catalog API views — CRUD endpoint demonstrating the service-layer pattern.

Deliverable 4.2: simple CRUD route + service-layer integration + validation.
Reads are public; writes require an authenticated vendor (or staff).
"""

from __future__ import annotations

from rest_framework import permissions, viewsets
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.permissions import SAFE_METHODS

from . import services
from .models import Product
from .serializers import ProductSerializer


class IsVendorOrReadOnly(permissions.BasePermission):
    """Anyone may read; only authenticated vendors/staff may write."""

    def has_permission(self, request, view) -> bool:
        if request.method in SAFE_METHODS:
            return True
        return bool(request.user and request.user.is_authenticated)

    def has_object_permission(self, request, view, obj: Product) -> bool:
        if request.method in SAFE_METHODS:
            return True
        # Object-level ownership: a vendor may only edit their own products.
        return bool(getattr(request.user, "is_staff", False)) or (
            getattr(obj.vendor, "owner_id", None) == request.user.id
        )


class ProductViewSet(viewsets.ModelViewSet):
    """`/api/v1/catalog/products/` — list, retrieve, create, update, delete."""

    serializer_class = ProductSerializer
    permission_classes = [IsVendorOrReadOnly]
    lookup_field = "slug"
    filterset_fields = ["category", "status"]
    search_fields = ["name", "sku"]
    ordering_fields = ["price", "created_at", "name"]
    ordering = ["-created_at"]

    def get_queryset(self):
        base = Product.objects.select_related("category", "vendor", "stock")
        user = self.request.user
        # Staff see everything; everyone else sees only active products.
        if user.is_authenticated and user.is_staff:
            return base
        return base.filter(status=Product.Status.ACTIVE)

    def _resolve_vendor(self):
        vendor = getattr(self.request.user, "vendor_profile", None)
        if vendor is None:
            raise PermissionDenied(
                "You need a vendor profile to manage products. " "Create one at /api/v1/vendors/."
            )
        if not vendor.is_active:
            raise PermissionDenied("Your vendor account is not approved yet.")
        return vendor

    def perform_create(self, serializer) -> None:
        vendor = self._resolve_vendor()
        # Enforce SKU uniqueness with a friendly error rather than a 500.
        sku = serializer.validated_data.get("sku")
        if Product.objects.filter(sku=sku).exists():
            raise ValidationError({"sku": "A product with this SKU already exists."})
        product = services.create_product(vendor=vendor, validated_data=serializer.validated_data)
        serializer.instance = product

    def perform_update(self, serializer) -> None:
        serializer.save()
        from django.core.cache import cache

        cache.delete(services.ACTIVE_PRODUCTS_CACHE_KEY)
