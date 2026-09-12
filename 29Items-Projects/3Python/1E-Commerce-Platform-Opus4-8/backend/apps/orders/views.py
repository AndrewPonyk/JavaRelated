"""Order + checkout API."""

from __future__ import annotations

from rest_framework import mixins, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response

from apps.cart.models import Cart

from . import services
from .models import Order, OrderItem


class OrderItemSerializer(serializers.ModelSerializer):
    product_name = serializers.CharField(source="product.name", read_only=True)

    class Meta:
        model = OrderItem
        fields = ["product", "product_name", "quantity", "unit_price"]


class OrderSerializer(serializers.ModelSerializer):
    items = OrderItemSerializer(many=True, read_only=True)

    class Meta:
        model = Order
        fields = [
            "id",
            "number",
            "status",
            "total_amount",
            "currency",
            "items",
            "item_count",
            "created_at",
        ]


class CheckoutInputSerializer(serializers.Serializer):
    cart_id = serializers.IntegerField(required=False)
    payment_token = serializers.CharField(default="tok_visa")


class OrderViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    serializer_class = OrderSerializer
    permission_classes = [IsAuthenticated]
    lookup_field = "number"

    def get_queryset(self):
        # `swagger_fake_view` is set during OpenAPI schema generation (no real user).
        if getattr(self, "swagger_fake_view", False):
            return Order.objects.none()
        return Order.objects.filter(user=self.request.user).prefetch_related("items__product")

    @action(detail=False, methods=["post"])
    def checkout(self, request: Request) -> Response:
        """POST /api/v1/orders/checkout/ — place an order from a cart."""
        payload = CheckoutInputSerializer(data=request.data)
        payload.is_valid(raise_exception=True)

        cart_id = payload.validated_data.get("cart_id")
        cart_qs = Cart.objects.filter(user=request.user, is_active=True)
        cart = cart_qs.filter(id=cart_id).first() if cart_id else cart_qs.first()
        if cart is None:
            return Response(
                {"error": {"code": "CART_NOT_FOUND", "message": "No active cart."}},
                status=status.HTTP_404_NOT_FOUND,
            )

        order = services.place_order(
            user=request.user,
            cart=cart,
            payment_token=payload.validated_data["payment_token"],
        )
        return Response(OrderSerializer(order).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"])
    def cancel(self, request: Request, number: str | None = None) -> Response:
        """POST /api/v1/orders/<number>/cancel/ — cancel a pending/paid order."""
        order = self.get_object()
        services.cancel_order(order=order)
        return Response(OrderSerializer(order).data)
