"""Inventory views for E-Commerce Platform."""
from django.db import models
from rest_framework import permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from .models import InventoryItem, InventoryTransaction, StockAlert
from .serializers import (
    InventoryItemSerializer,
    InventoryTransactionSerializer,
    StockAdjustmentSerializer,
    StockAlertSerializer,
)
from .services import InventoryService


class IsVendorOrAdmin(permissions.BasePermission):
    """Permission class for vendor or admin access."""

    def has_permission(self, request, view):
        return request.user.is_authenticated and (
            request.user.is_staff or hasattr(request.user, 'vendor')
        )


class InventoryViewSet(viewsets.ModelViewSet):
    """ViewSet for inventory management."""

    serializer_class = InventoryItemSerializer
    permission_classes = [IsVendorOrAdmin]

    def get_queryset(self):
        """Filter inventory by vendor if applicable."""
        queryset = InventoryItem.objects.select_related('product', 'variant')

        # Filter by vendor for vendor users (non-admin)
        if hasattr(self.request.user, 'vendor') and not self.request.user.is_staff:
            queryset = queryset.filter(product__vendor=self.request.user.vendor)

        return queryset

    @action(detail=False, methods=['get'])
    def low_stock(self, request):
        """Get items with low stock (paginated)."""
        items = self.get_queryset().filter(
            quantity_available__lte=models.F('reorder_point')
        ).order_by('quantity_available')

        page = self.paginate_queryset(items)
        if page is not None:
            serializer = self.get_serializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = self.get_serializer(items, many=True)
        return Response(serializer.data)

    @action(detail=False, methods=['get'])
    def out_of_stock(self, request):
        """Get out of stock items (paginated)."""
        items = self.get_queryset().filter(quantity_available=0).order_by('-updated_at')

        page = self.paginate_queryset(items)
        if page is not None:
            serializer = self.get_serializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = self.get_serializer(items, many=True)
        return Response(serializer.data)

    @action(detail=False, methods=['post'])
    def adjust(self, request):
        """Make manual stock adjustment."""
        serializer = StockAdjustmentSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        try:
            transaction = InventoryService.adjust_stock(
                inventory_item_id=serializer.validated_data['inventory_item_id'],
                quantity=serializer.validated_data['adjustment_quantity'],
                reason=serializer.validated_data['reason'],
                user=request.user
            )
            return Response(
                InventoryTransactionSerializer(transaction).data,
                status=status.HTTP_201_CREATED
            )
        except ValueError as e:
            return Response(
                {'error': str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )

    @action(detail=True, methods=['get'])
    def transactions(self, request, pk=None):
        """Get transaction history for an inventory item (paginated)."""
        item = self.get_object()
        transactions = item.transactions.select_related('created_by').order_by('-created_at')

        page = self.paginate_queryset(transactions)
        if page is not None:
            serializer = InventoryTransactionSerializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = InventoryTransactionSerializer(transactions[:50], many=True)
        return Response(serializer.data)


class StockAlertViewSet(viewsets.ReadOnlyModelViewSet):
    """ViewSet for stock alerts."""

    serializer_class = StockAlertSerializer
    permission_classes = [IsVendorOrAdmin]

    VALID_STATUSES = {'pending', 'acknowledged', 'resolved'}

    def get_queryset(self):
        queryset = StockAlert.objects.select_related(
            'inventory_item__product'
        ).order_by('-created_at')

        # Filter by vendor for vendor users
        if hasattr(self.request.user, 'vendor') and not self.request.user.is_staff:
            queryset = queryset.filter(
                inventory_item__product__vendor=self.request.user.vendor
            )

        status_filter = self.request.query_params.get('status')
        if status_filter:
            # Validate status parameter
            if status_filter not in self.VALID_STATUSES:
                return queryset.none()
            queryset = queryset.filter(status=status_filter)

        return queryset

    @action(detail=True, methods=['post'])
    def acknowledge(self, request, pk=None):
        """Acknowledge a stock alert."""
        alert = self.get_object()
        InventoryService.acknowledge_alert(alert)
        return Response(self.get_serializer(alert).data)

    @action(detail=True, methods=['post'])
    def resolve(self, request, pk=None):
        """Resolve a stock alert."""
        alert = self.get_object()
        InventoryService.resolve_alert(alert)
        return Response(self.get_serializer(alert).data)
