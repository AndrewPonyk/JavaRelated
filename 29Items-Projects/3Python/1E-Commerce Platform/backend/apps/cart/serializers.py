"""Cart serializers for E-Commerce Platform."""
from rest_framework import serializers

from apps.products.serializers import ProductListSerializer, ProductVariantSerializer

from .models import Cart, CartItem


class CartItemSerializer(serializers.ModelSerializer):
    """Serializer for cart items."""

    product = ProductListSerializer(read_only=True)
    variant = ProductVariantSerializer(read_only=True)
    line_total = serializers.DecimalField(
        max_digits=10,
        decimal_places=2,
        read_only=True
    )

    class Meta:
        model = CartItem
        fields = [
            'id', 'product', 'variant', 'quantity',
            'unit_price', 'line_total', 'added_at'
        ]
        read_only_fields = ['unit_price', 'added_at']


class CartItemCreateSerializer(serializers.Serializer):
    """Serializer for adding items to cart."""

    product_id = serializers.UUIDField()
    variant_id = serializers.IntegerField(required=False, allow_null=True)
    quantity = serializers.IntegerField(min_value=1, default=1)


class CartItemUpdateSerializer(serializers.Serializer):
    """Serializer for updating cart item quantity."""

    quantity = serializers.IntegerField(min_value=1)


class CartSerializer(serializers.ModelSerializer):
    """Serializer for shopping cart."""

    items = CartItemSerializer(many=True, read_only=True)
    item_count = serializers.IntegerField(read_only=True)
    subtotal = serializers.DecimalField(
        max_digits=10,
        decimal_places=2,
        read_only=True
    )
    is_empty = serializers.BooleanField(read_only=True)

    class Meta:
        model = Cart
        fields = [
            'id', 'items', 'item_count', 'subtotal',
            'is_empty', 'created_at', 'updated_at'
        ]
