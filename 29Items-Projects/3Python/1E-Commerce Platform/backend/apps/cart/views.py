"""Cart views for E-Commerce Platform."""
from rest_framework import permissions, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework.viewsets import ViewSet

from .models import Cart, CartItem
from .serializers import (
    CartItemCreateSerializer,
    CartItemUpdateSerializer,
    CartSerializer,
)
from .services import CartService


class CartViewSet(ViewSet):
    """ViewSet for shopping cart operations."""

    permission_classes = [permissions.AllowAny]

    def get_cart(self, request):
        """Get or create cart for current user/session."""
        return CartService.get_or_create_cart(request)

    def list(self, request):
        """Get current cart."""
        cart = self.get_cart(request)
        serializer = CartSerializer(cart)
        return Response(serializer.data)

    @action(detail=False, methods=['post'])
    def add(self, request):
        """Add item to cart."""
        serializer = CartItemCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        cart = self.get_cart(request)
        try:
            cart_item = CartService.add_item(
                cart=cart,
                product_id=serializer.validated_data['product_id'],
                variant_id=serializer.validated_data.get('variant_id'),
                quantity=serializer.validated_data['quantity']
            )
            return Response(
                CartSerializer(cart).data,
                status=status.HTTP_201_CREATED
            )
        except ValueError as e:
            return Response(
                {'error': str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )

    @action(detail=False, methods=['patch'], url_path='items/(?P<item_id>[^/.]+)')
    def update_item(self, request, item_id=None):
        """Update cart item quantity."""
        serializer = CartItemUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        cart = self.get_cart(request)
        try:
            CartService.update_item_quantity(
                cart=cart,
                item_id=item_id,
                quantity=serializer.validated_data['quantity']
            )
            # Refresh cart from database to get updated data
            cart = self.get_cart(request)
            return Response(CartSerializer(cart).data)
        except (CartItem.DoesNotExist, ValueError) as e:
            return Response(
                {'error': str(e) if isinstance(e, ValueError) else 'Item not found in cart.'},
                status=status.HTTP_404_NOT_FOUND
            )

    @action(detail=False, methods=['delete'], url_path='items/(?P<item_id>[^/.]+)/remove')
    def remove_item(self, request, item_id=None):
        """Remove item from cart."""
        cart = self.get_cart(request)
        try:
            CartService.remove_item(cart=cart, item_id=item_id)
            # Refresh cart from database to get updated items
            cart.refresh_from_db()
            cart = self.get_cart(request)  # Re-fetch with prefetch
            return Response(CartSerializer(cart).data)
        except (CartItem.DoesNotExist, ValueError):
            return Response(
                {'error': 'Item not found in cart.'},
                status=status.HTTP_404_NOT_FOUND
            )

    @action(detail=False, methods=['post'])
    def clear(self, request):
        """Clear all items from cart."""
        cart = self.get_cart(request)
        CartService.clear_cart(cart)
        return Response(CartSerializer(cart).data)

    @action(detail=False, methods=['post'])
    def merge(self, request):
        """Merge guest cart into user cart after login."""
        if not request.user.is_authenticated:
            return Response(
                {'error': 'Authentication required.'},
                status=status.HTTP_401_UNAUTHORIZED
            )

        session_key = request.session.session_key
        if session_key:
            CartService.merge_guest_cart(
                user=request.user,
                session_key=session_key
            )

        cart = self.get_cart(request)
        return Response(CartSerializer(cart).data)
