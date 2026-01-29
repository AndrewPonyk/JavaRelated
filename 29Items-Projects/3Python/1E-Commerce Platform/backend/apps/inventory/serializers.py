"""Inventory serializers for E-Commerce Platform."""
from rest_framework import serializers

from .models import InventoryItem, InventoryTransaction, StockAlert


class InventoryItemSerializer(serializers.ModelSerializer):
    """Serializer for inventory items."""

    product_name = serializers.CharField(source='product.name', read_only=True)
    variant_name = serializers.CharField(source='variant.name', read_only=True)
    is_low_stock = serializers.BooleanField(read_only=True)
    is_out_of_stock = serializers.BooleanField(read_only=True)

    class Meta:
        model = InventoryItem
        fields = [
            'id', 'product', 'product_name', 'variant', 'variant_name',
            'quantity_on_hand', 'quantity_reserved', 'quantity_available',
            'reorder_point', 'reorder_quantity',
            'is_low_stock', 'is_out_of_stock',
            'last_restocked_at', 'updated_at'
        ]
        read_only_fields = ['quantity_available']


class InventoryTransactionSerializer(serializers.ModelSerializer):
    """Serializer for inventory transactions."""

    class Meta:
        model = InventoryTransaction
        fields = [
            'id', 'inventory_item', 'transaction_type',
            'quantity', 'reference_type', 'reference_id',
            'notes', 'created_at', 'created_by'
        ]
        read_only_fields = ['created_at', 'created_by']


class StockAdjustmentSerializer(serializers.Serializer):
    """Serializer for manual stock adjustments."""

    inventory_item_id = serializers.IntegerField()
    adjustment_quantity = serializers.IntegerField()
    reason = serializers.CharField()


class StockAlertSerializer(serializers.ModelSerializer):
    """Serializer for stock alerts."""

    product_name = serializers.CharField(
        source='inventory_item.product.name',
        read_only=True
    )

    class Meta:
        model = StockAlert
        fields = [
            'id', 'inventory_item', 'product_name', 'status',
            'threshold_triggered', 'current_quantity',
            'created_at', 'acknowledged_at', 'resolved_at'
        ]
