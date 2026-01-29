"""Vendor views for E-Commerce Platform."""
from rest_framework import permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Vendor, VendorPayout
from .serializers import (
    VendorDashboardSerializer,
    VendorPayoutSerializer,
    VendorPublicSerializer,
    VendorRegistrationSerializer,
    VendorSerializer,
)


class IsVendor(permissions.BasePermission):
    """Permission check for vendor users."""

    def has_permission(self, request, view):
        return (
            request.user.is_authenticated and
            hasattr(request.user, 'vendor') and
            request.user.vendor.status == Vendor.Status.ACTIVE
        )


class VendorRegistrationView(APIView):
    """View for vendor registration."""

    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        if hasattr(request.user, 'vendor'):
            return Response(
                {'error': 'You are already registered as a vendor.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        serializer = VendorRegistrationSerializer(
            data=request.data,
            context={'request': request}
        )
        if serializer.is_valid():
            vendor = serializer.save()
            return Response(
                VendorSerializer(vendor).data,
                status=status.HTTP_201_CREATED
            )
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


class VendorProfileView(APIView):
    """View for vendor profile management."""

    permission_classes = [IsVendor]

    def get(self, request):
        serializer = VendorSerializer(request.user.vendor)
        return Response(serializer.data)

    def patch(self, request):
        serializer = VendorSerializer(
            request.user.vendor,
            data=request.data,
            partial=True
        )
        if serializer.is_valid():
            serializer.save()
            return Response(serializer.data)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


class VendorDashboardView(APIView):
    """View for vendor dashboard data."""

    permission_classes = [IsVendor]

    def get(self, request):
        serializer = VendorDashboardSerializer(request.user.vendor)
        return Response(serializer.data)


class VendorPublicViewSet(viewsets.ReadOnlyModelViewSet):
    """ViewSet for public vendor listings."""

    queryset = Vendor.objects.filter(status=Vendor.Status.ACTIVE)
    serializer_class = VendorPublicSerializer
    permission_classes = [permissions.AllowAny]
    lookup_field = 'slug'

    @action(detail=True, methods=['get'])
    def products(self, request, slug=None):
        """Get products for a vendor (paginated)."""
        from apps.products.models import Product
        from apps.products.serializers import ProductListSerializer

        vendor = self.get_object()
        products = Product.objects.filter(
            vendor=vendor,
            status=Product.Status.ACTIVE
        ).select_related('category').prefetch_related('images')

        page = self.paginate_queryset(products)
        if page is not None:
            serializer = ProductListSerializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = ProductListSerializer(products, many=True)
        return Response(serializer.data)


class VendorPayoutViewSet(viewsets.ReadOnlyModelViewSet):
    """ViewSet for vendor payouts."""

    serializer_class = VendorPayoutSerializer
    permission_classes = [IsVendor]

    def get_queryset(self):
        return VendorPayout.objects.filter(vendor=self.request.user.vendor)
