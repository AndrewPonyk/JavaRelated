"""
Vendor Controller (ViewSets)

REST API endpoints for vendor management.
"""

from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.db.models import Count, Sum, F

from backend.core.models import Vendor, Product, Order, OrderItem
from backend.api.serializers import VendorSerializer
from backend.api.permissions import IsVendorOrReadOnly


class VendorViewSet(viewsets.ReadOnlyModelViewSet):
    """
    ViewSet for vendor operations.
    """

    queryset = Vendor.objects.filter(is_active=True)
    serializer_class = VendorSerializer
    permission_classes = [IsAuthenticated]
    lookup_field = "slug"

    @action(detail=True, methods=["get"])
    def products(self, request, slug=None):
        """Get all products for this vendor."""
        vendor = self.get_object()
        products = vendor.products.filter(is_active=True)

        # Apply pagination
        from backend.api.pagination import StandardResultsSetPagination

        paginator = StandardResultsSetPagination()
        page = paginator.paginate_queryset(products, request)

        from backend.api.serializers import ProductSerializer

        serializer = ProductSerializer(page, many=True)
        return paginator.get_paginated_response(serializer.data)

    @action(detail=False, methods=["get"])
    def dashboard(self, request):
        """Get vendor dashboard data."""
        if not request.user.is_vendor:
            return Response(
                {"error": "User is not a vendor"},
                status=status.HTTP_403_FORBIDDEN,
            )

        try:
            vendor = request.user.vendor_profile
        except Vendor.DoesNotExist:
            return Response(
                {"error": "Vendor profile not found"},
                status=status.HTTP_404_NOT_FOUND,
            )

        # Calculate stats
        products = vendor.products.filter(is_active=True)
        total_products = products.count()

        # TODO: Calculate sales from OrderItems
        total_sales = 0
        total_orders = 0
        pending_orders = 0

        return Response({
            "vendor": VendorSerializer(vendor).data,
            "stats": {
                "total_products": total_products,
                "total_sales": total_sales,
                "total_orders": total_orders,
                "pending_orders": pending_orders,
            },
        })

    @action(detail=False, methods=["post"])
    def register(self, request):
        """Register as a vendor."""
        if request.user.is_vendor:
            return Response(
                {"error": "User is already a vendor"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # TODO: Implement vendor registration flow
        return Response(
            {"message": "Vendor registration coming soon"},
            status=status.HTTP_501_NOT_IMPLEMENTED,
        )


# TODO: Add vendor product management endpoints
# TODO: Add vendor order management endpoints
# TODO: Add vendor analytics endpoints
