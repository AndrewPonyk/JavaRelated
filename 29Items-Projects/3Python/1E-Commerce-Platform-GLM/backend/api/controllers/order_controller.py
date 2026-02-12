"""
Order Controller (ViewSets)

REST API endpoints for order management.
"""

from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.shortcuts import get_object_or_404
from drf_spectacular.utils import extend_schema, OpenApiParameter

from backend.core.models import Order, OrderStatus
from backend.api.serializers import OrderSerializer, OrderDetailSerializer
from backend.api.services.order_service import OrderService
from backend.api.validators.order_validator import OrderValidator


class OrderViewSet(viewsets.ModelViewSet):
    """
    ViewSet for order management.
    """

    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        """Get orders for the current user."""
        return Order.objects.filter(user=self.request.user)

    def get_serializer_class(self):
        """Return appropriate serializer based on action."""
        if self.action in ["retrieve", "checkout"]:
            return OrderDetailSerializer
        return OrderSerializer

    def list(self, request):
        """List user's orders with optional filtering."""
        queryset = self.get_queryset()

        # Filter by status if provided
        status_filter = request.query_params.get("status")
        if status_filter:
            queryset = queryset.filter(status=status_filter)

        # Pagination
        page = self.paginate_queryset(queryset)
        if page is not None:
            serializer = self.get_serializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = self.get_serializer(queryset, many=True)
        return Response(serializer.data)

    def retrieve(self, request, pk=None):
        """Get a single order by ID."""
        order = get_object_or_404(Order, pk=pk, user=request.user)
        serializer = self.get_serializer(order)
        return Response(serializer.data)

    @extend_schema(
        request=OrderValidator.checkout,
        responses={201: OrderDetailSerializer},
    )
    @action(detail=False, methods=["post"])
    def checkout(self, request):
        """
        Process checkout and create a new order.

        Validates cart items, calculates totals, and creates an order.
        """
        validator = OrderValidator.checkout(data=request.data)
        validator.is_valid(raise_exception=True)

        # Process checkout via service
        try:
            order = OrderService.create_order(
                user=request.user,
                cart_data=validator.validated_data,
            )

            # Process payment asynchronously
            from backend.tasks.payment_tasks import process_payment

            process_payment.delay(order.id, validator.validated_data.get("payment_token"))

            serializer = OrderDetailSerializer(order)
            return Response(serializer.data, status=status.HTTP_201_CREATED)

        except ValueError as e:
            return Response(
                {"error": str(e)},
                status=status.HTTP_400_BAD_REQUEST,
            )

    @action(detail=True, methods=["post"])
    def cancel(self, request, pk=None):
        """Cancel an order."""
        order = get_object_or_404(Order, pk=pk, user=request.user)

        if order.status in [OrderStatus.SHIPPED, OrderStatus.DELIVERED]:
            return Response(
                {"error": "Cannot cancel shipped or delivered orders"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        order.cancel()
        serializer = OrderDetailSerializer(order)
        return Response(serializer.data)

    @action(detail=True, methods=["get"])
    def tracking(self, request, pk=None):
        """Get tracking information for an order."""
        order = get_object_or_404(Order, pk=pk, user=request.user)

        tracking_info = {
            "order_id": order.id,
            "order_number": order.order_number,
            "status": order.status,
            "estimated_delivery": order.estimated_delivery,
            "tracking_number": order.tracking_number,
            "carrier": order.carrier,
            "history": self._get_tracking_history(order),
        }

        return Response(tracking_info)

    def _get_tracking_history(self, order):
        """Get tracking history for the order."""
        history = []

        # Add created event
        history.append({
            "timestamp": order.created_at,
            "status": "created",
            "description": "Order placed",
        })

        # Add confirmed event
        if order.confirmed_at:
            history.append({
                "timestamp": order.confirmed_at,
                "status": "confirmed",
                "description": "Order confirmed",
            })

        # Add shipped event
        if order.shipped_at:
            history.append({
                "timestamp": order.shipped_at,
                "status": "shipped",
                "description": f"Shipped via {order.carrier}",
                "location": None,
            })

        # Add delivered event
        if order.delivered_at:
            history.append({
                "timestamp": order.delivered_at,
                "status": "delivered",
                "description": "Delivered",
            })

        # Add cancelled event
        if order.cancelled_at:
            history.append({
                "timestamp": order.cancelled_at,
                "status": "cancelled",
                "description": "Order cancelled",
            })

        return sorted(history, key=lambda x: x["timestamp"], reverse=True)
