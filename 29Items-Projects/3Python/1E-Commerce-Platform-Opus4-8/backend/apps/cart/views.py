"""Cart API — current cart plus add/update/remove/clear line-item operations."""

from __future__ import annotations

from drf_spectacular.utils import extend_schema
from rest_framework import status
from rest_framework.generics import get_object_or_404
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView

from . import services
from .models import CartItem
from .serializers import (
    AddItemSerializer,
    CartSerializer,
    UpdateItemSerializer,
)


class CartView(APIView):
    """GET /api/v1/cart/ — the authenticated user's active cart."""

    permission_classes = [IsAuthenticated]

    @extend_schema(responses=CartSerializer)
    def get(self, request: Request) -> Response:
        cart = services.get_or_create_active_cart(user=request.user)
        return Response(CartSerializer(cart).data)

    @extend_schema(responses=CartSerializer)
    def delete(self, request: Request) -> Response:
        """DELETE /api/v1/cart/ — clear all items."""
        cart = services.get_or_create_active_cart(user=request.user)
        services.clear(cart=cart)
        return Response(CartSerializer(cart).data)


class CartItemsView(APIView):
    """POST /api/v1/cart/items/ — add a product to the cart."""

    permission_classes = [IsAuthenticated]

    @extend_schema(request=AddItemSerializer, responses=CartSerializer)
    def post(self, request: Request) -> Response:
        payload = AddItemSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        cart = services.get_or_create_active_cart(user=request.user)
        services.add_item(
            cart=cart,
            product_id=payload.validated_data["product_id"],
            quantity=payload.validated_data["quantity"],
        )
        return Response(CartSerializer(cart).data, status=status.HTTP_201_CREATED)


class CartItemDetailView(APIView):
    """PATCH/DELETE /api/v1/cart/items/<pk>/ — update or remove a line item."""

    permission_classes = [IsAuthenticated]

    def _get_item(self, request: Request, pk: int) -> CartItem:
        return get_object_or_404(CartItem, pk=pk, cart__user=request.user, cart__is_active=True)

    @extend_schema(request=UpdateItemSerializer, responses=CartSerializer)
    def patch(self, request: Request, pk: int) -> Response:
        item = self._get_item(request, pk)
        payload = UpdateItemSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        services.update_item(item=item, quantity=payload.validated_data["quantity"])
        return Response(CartSerializer(item.cart).data)

    @extend_schema(responses=CartSerializer)
    def delete(self, request: Request, pk: int) -> Response:
        item = self._get_item(request, pk)
        cart = item.cart
        services.remove_item(item=item)
        return Response(CartSerializer(cart).data)
