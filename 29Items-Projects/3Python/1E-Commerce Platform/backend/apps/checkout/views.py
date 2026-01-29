"""Checkout views for E-Commerce Platform."""
from rest_framework import permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Coupon, Order
from .serializers import (
    CheckoutSerializer,
    CouponSerializer,
    CouponValidateSerializer,
    OrderListSerializer,
    OrderSerializer,
    PaymentConfirmSerializer,
)
from .services import CheckoutService, CouponService


class OrderViewSet(viewsets.ReadOnlyModelViewSet):
    """ViewSet for viewing orders."""

    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        queryset = Order.objects.filter(user=self.request.user).prefetch_related('items')
        if self.action == 'retrieve':
            queryset = queryset.prefetch_related(
                'items__product',
                'items__variant',
                'status_history'
            )
        return queryset

    def get_serializer_class(self):
        if self.action == 'list':
            return OrderListSerializer
        return OrderSerializer

    @action(detail=True, methods=['post'])
    def cancel(self, request, pk=None):
        """Cancel an order if eligible."""
        order = self.get_object()
        try:
            CheckoutService.cancel_order(order, request.user)
            return Response(OrderSerializer(order).data)
        except ValueError as e:
            return Response(
                {'error': str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )


class CheckoutView(APIView):
    """View for checkout process."""

    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        """Initiate checkout and create payment intent."""
        serializer = CheckoutSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        try:
            result = CheckoutService.initiate_checkout(
                user=request.user,
                checkout_data=serializer.validated_data
            )
            return Response(result, status=status.HTTP_200_OK)
        except ValueError as e:
            return Response(
                {'error': str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )


class PaymentConfirmView(APIView):
    """View for confirming payment and creating order."""

    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        """Confirm payment and finalize order."""
        serializer = PaymentConfirmSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        try:
            order = CheckoutService.confirm_payment(
                user=request.user,
                payment_data=serializer.validated_data
            )
            return Response(
                OrderSerializer(order).data,
                status=status.HTTP_201_CREATED
            )
        except ValueError as e:
            return Response(
                {'error': str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )


class CouponValidateView(APIView):
    """View for validating coupon codes."""

    permission_classes = [permissions.AllowAny]

    def post(self, request):
        """Validate a coupon code."""
        serializer = CouponValidateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        cart_total = request.data.get('cart_total', 0)

        try:
            coupon = CouponService.validate_coupon(
                code=serializer.validated_data['code'],
                user=request.user if request.user.is_authenticated else None,
                cart_total=cart_total
            )
            discount = CouponService.calculate_discount(coupon, cart_total)
            return Response({
                'valid': True,
                'coupon': CouponSerializer(coupon).data,
                'discount_amount': discount
            })
        except ValueError as e:
            return Response(
                {'valid': False, 'error': str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )
