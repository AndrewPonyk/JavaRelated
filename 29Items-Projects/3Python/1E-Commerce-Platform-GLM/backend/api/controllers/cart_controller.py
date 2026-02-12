"""
Cart Controller (ViewSets)

REST API endpoints for shopping cart.
"""

from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from rest_framework.exceptions import ValidationError

from backend.core.models import Cart, CartItem, Product, Wishlist, WishlistItem, RecentlyViewed
from backend.api.serializers import CartSerializer, CartItemSerializer, WishlistSerializer, WishlistItemSerializer, RecentlyViewedSerializer
from backend.api.services.cart_service import CartService


class CartViewSet(viewsets.ViewSet):
    """
    ViewSet for shopping cart operations.
    """

    permission_classes = [IsAuthenticated]
    serializer_class = CartSerializer

    def _get_cart(self):
        """Get or create the user's cart."""
        cart, _ = Cart.objects.get_or_create(user=self.request.user)
        return cart

    def list(self, request):
        """Get the user's cart."""
        cart = self._get_cart()
        serializer = CartSerializer(cart)
        return Response(serializer.data)

    @action(detail=False, methods=["post"])
    def add_item(self, request):
        """Add an item to the cart."""
        cart = self._get_cart()
        product_id = request.data.get("product_id")
        quantity = request.data.get("quantity", 1)

        if not product_id:
            raise ValidationError("product_id is required")

        # Validate product exists and is active
        try:
            product = Product.objects.get(id=product_id, is_active=True)
        except Product.DoesNotExist:
            raise ValidationError("Product not found or inactive")

        # Check stock
        if product.stock < quantity:
            raise ValidationError(f"Only {product.stock} items available")

        # Add or update item
        item = cart.add_item(product_id, quantity)

        # Return updated cart
        serializer = CartSerializer(cart)
        return Response(serializer.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["patch", "put"])
    def update_item(self, request, pk=None):
        """Update cart item quantity."""
        cart = self._get_cart()
        quantity = request.data.get("quantity", 1)

        if quantity < 1:
            return Response(
                {"error": "Quantity must be at least 1"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            item = CartItem.objects.get(cart=cart, id=pk)
            item.quantity = quantity
            item.save()
        except CartItem.DoesNotExist:
            return Response(
                {"error": "Cart item not found"},
                status=status.HTTP_404_NOT_FOUND,
            )

        serializer = CartSerializer(cart)
        return Response(serializer.data)

    @action(detail=True, methods=["delete"])
    def remove_item(self, request, pk=None):
        """Remove an item from the cart."""
        cart = self._get_cart()
        try:
            CartItem.objects.get(cart=cart, id=pk).delete()
        except CartItem.DoesNotExist:
            return Response(
                {"error": "Cart item not found"},
                status=status.HTTP_404_NOT_FOUND,
            )

        serializer = CartSerializer(cart)
        return Response(serializer.data)

    @action(detail=False, methods=["delete"])
    def clear(self, request):
        """Clear all items from the cart."""
        cart = self._get_cart()
        cart.clear()

        serializer = CartSerializer(cart)
        return Response(serializer.data)

    @action(detail=False, methods=["post"])
    def merge(self, request):
        """Merge guest cart (from session) with user cart."""
        session_key = request.data.get("session_key")
        if not session_key:
            return Response(
                {"error": "session_key is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            guest_cart = Cart.objects.get(session_key=session_key)
            user_cart = self._get_cart()

            # Merge items
            for item in guest_cart.items.all():
                user_cart.add_item(item.product_id, item.quantity)

            # Delete guest cart
            guest_cart.delete()

            serializer = CartSerializer(user_cart)
            return Response(serializer.data)

        except Cart.DoesNotExist:
            # No guest cart to merge, return user cart as is
            serializer = CartSerializer(self._get_cart())
            return Response(serializer.data)

    # ==========================================================================
    # Wishlist Endpoints
    # ==========================================================================

    @action(detail=False, methods=["get"])
    def wishlist(self, request):
        """Get user's default wishlist."""
        wishlist, _ = Wishlist.objects.get_or_create(
            user=request.user,
            defaults={"name": "My Wishlist", "is_default": True}
        )
        serializer = WishlistSerializer(wishlist)
        return Response(serializer.data)

    @action(detail=False, methods=["post"])
    def add_to_wishlist(self, request):
        """Add product to wishlist."""
        product_id = request.data.get("product_id")
        if not product_id:
            return Response(
                {"error": "product_id is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            Product.objects.get(id=product_id, is_active=True)
        except Product.DoesNotExist:
            return Response(
                {"error": "Product not found or inactive"},
                status=status.HTTP_404_NOT_FOUND,
            )

        wishlist, _ = Wishlist.objects.get_or_create(
            user=request.user,
            defaults={"name": "My Wishlist", "is_default": True}
        )

        wishlist.add_product(product_id)
        serializer = WishlistSerializer(wishlist)
        return Response(serializer.data, status=status.HTTP_201_CREATED)

    @action(detail=False, methods=["delete"])
    def remove_from_wishlist(self, request):
        """Remove product from wishlist."""
        product_id = request.query_params.get("product_id")
        if not product_id:
            return Response(
                {"error": "product_id is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        wishlist, _ = Wishlist.objects.get_or_create(
            user=request.user,
            defaults={"name": "My Wishlist", "is_default": True}
        )

        if wishlist.remove_product(product_id):
            serializer = WishlistSerializer(wishlist)
            return Response(serializer.data)

        return Response(
            {"error": "Product not in wishlist"},
            status=status.HTTP_404_NOT_FOUND,
        )

    @action(detail=False, methods=["post"])
    def move_wishlist_to_cart(self, request):
        """Move product from wishlist to cart."""
        product_id = request.data.get("product_id")
        if not product_id:
            return Response(
                {"error": "product_id is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        wishlist, _ = Wishlist.objects.get_or_create(
            user=request.user,
            defaults={"name": "My Wishlist", "is_default": True}
        )

        if wishlist.move_to_cart(product_id, self._get_cart().id):
            serializer = CartSerializer(self._get_cart())
            return Response(serializer.data)

        return Response(
            {"error": "Product not in wishlist or unable to add to cart"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    @action(detail=False, methods=["delete"])
    def clear_wishlist(self, request):
        """Clear all items from wishlist."""
        wishlist, _ = Wishlist.objects.get_or_create(
            user=request.user,
            defaults={"name": "My Wishlist", "is_default": True}
        )
        wishlist.clear()

        serializer = WishlistSerializer(wishlist)
        return Response(serializer.data)

    # ==========================================================================
    # Recently Viewed Endpoints
    # ==========================================================================

    @action(detail=False, methods=["get"])
    def recently_viewed(self, request):
        """Get recently viewed products."""
        limit = int(request.query_params.get("limit", 10))
        products = RecentlyViewed.get_recent_products(request.user.id, limit)

        from backend.api.serializers import ProductSerializer
        serializer = ProductSerializer(products, many=True)
        return Response(serializer.data)

    @action(detail=False, methods=["post"])
    def add_viewed(self, request):
        """Track product view."""
        product_id = request.data.get("product_id")
        if not product_id:
            return Response(
                {"error": "product_id is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        RecentlyViewed.add_view(request.user.id, product_id)
        return Response({"message": "View tracked"})

    @action(detail=False, methods=["delete"])
    def clear_viewed(self, request):
        """Clear recently viewed history."""
        keep = int(request.query_params.get("keep", 50))
        deleted = RecentlyViewed.clear_old_views(request.user.id, keep)
        return Response({"message": f"Cleared {deleted} old views"})
