"""Checkout serializers for E-Commerce Platform."""
from rest_framework import serializers

from .models import Coupon, Order, OrderItem


class OrderItemSerializer(serializers.ModelSerializer):
    """Serializer for order items."""

    class Meta:
        model = OrderItem
        fields = [
            'id', 'product_name', 'product_sku', 'variant_name',
            'unit_price', 'quantity', 'line_total', 'fulfilled_quantity'
        ]


class OrderSerializer(serializers.ModelSerializer):
    """Serializer for orders."""

    items = OrderItemSerializer(many=True, read_only=True)

    class Meta:
        model = Order
        fields = [
            'id', 'order_number', 'email', 'status', 'payment_status',
            'subtotal', 'tax_amount', 'shipping_amount', 'discount_amount', 'total',
            'shipping_address', 'billing_address',
            'customer_notes', 'items',
            'created_at', 'paid_at', 'shipped_at', 'delivered_at'
        ]


class OrderListSerializer(serializers.ModelSerializer):
    """Lightweight serializer for order listings."""

    items = OrderItemSerializer(many=True, read_only=True)
    item_count = serializers.SerializerMethodField()

    class Meta:
        model = Order
        fields = [
            'id', 'order_number', 'status', 'payment_status',
            'total', 'item_count', 'items', 'created_at',
            'shipped_at', 'delivered_at'
        ]

    def get_item_count(self, obj):
        return obj.items.count()


class CheckoutAddressSerializer(serializers.Serializer):
    """Serializer for checkout address (not tied to Address model)."""

    full_name = serializers.CharField(required=False, allow_blank=True)
    street_address = serializers.CharField(required=True)
    apartment = serializers.CharField(required=False, allow_blank=True)
    city = serializers.CharField(required=True)
    state = serializers.CharField(required=True)
    postal_code = serializers.CharField(required=True)
    country = serializers.CharField(required=True)
    phone = serializers.CharField(required=False, allow_blank=True)


class CheckoutSerializer(serializers.Serializer):
    """Serializer for checkout initiation."""

    shipping_address_id = serializers.IntegerField(required=False)
    billing_address_id = serializers.IntegerField(required=False)
    shipping_address = CheckoutAddressSerializer(required=False)
    billing_address = CheckoutAddressSerializer(required=False)
    same_as_shipping = serializers.BooleanField(default=True)
    customer_notes = serializers.CharField(required=False, allow_blank=True)
    coupon_code = serializers.CharField(required=False, allow_blank=True)

    def validate(self, attrs):
        """Ensure address is provided either as ID or full address."""
        has_shipping_id = attrs.get('shipping_address_id')
        has_shipping_data = attrs.get('shipping_address')

        if not has_shipping_id and not has_shipping_data:
            raise serializers.ValidationError(
                {'shipping_address': 'Shipping address is required.'}
            )

        return attrs


class PaymentConfirmSerializer(serializers.Serializer):
    """Serializer for payment confirmation."""

    payment_intent_id = serializers.CharField()
    payment_method_id = serializers.CharField(required=False)


class CouponSerializer(serializers.ModelSerializer):
    """Serializer for coupons."""

    class Meta:
        model = Coupon
        fields = [
            'code', 'discount_type', 'discount_value',
            'minimum_order_amount', 'maximum_discount_amount'
        ]


class CouponValidateSerializer(serializers.Serializer):
    """Serializer for coupon validation."""

    code = serializers.CharField()
