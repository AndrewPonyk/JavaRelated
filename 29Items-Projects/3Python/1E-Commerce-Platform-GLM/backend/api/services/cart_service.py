"""
Cart Service

Business logic for shopping cart operations.
"""

from typing import Optional
from django.core.cache import cache
from django.db import transaction

from backend.core.models import Cart, CartItem, Product


class CartService:
    """Service class for cart-related business logic."""

    def __init__(self):
        self.cache_prefix = "cart"
        self.cache_ttl = 3600  # 1 hour

    def get_user_cart(self, user_id: int) -> Cart:
        """
        Get or create user's cart.

        Args:
            user_id: The user ID

        Returns:
            Cart object
        """
        cart, _ = Cart.objects.get_or_create(user_id=user_id)
        return cart

    def get_guest_cart(self, session_key: str) -> Cart:
        """
        Get or create guest cart.

        Args:
            session_key: The session key

        Returns:
            Cart object
        """
        cart, _ = Cart.objects.get_or_create(session_key=session_key)
        return cart

    def add_item(
        self,
        cart: Cart,
        product_id: int,
        quantity: int = 1,
    ) -> CartItem:
        """
        Add item to cart or update quantity if exists.

        Args:
            cart: The cart object
            product_id: The product ID
            quantity: Quantity to add

        Returns:
            CartItem object

        Raises:
            ValueError: If product is inactive or insufficient stock
        """
        try:
            product = Product.objects.get(id=product_id, is_active=True)
        except Product.DoesNotExist:
            raise ValueError("Product not found or inactive")

        if product.stock < quantity:
            raise ValueError(f"Only {product.stock} items available")

        with transaction.atomic():
            item, created = CartItem.objects.get_or_create(
                cart=cart,
                product=product,
                defaults={"quantity": quantity, "unit_price": product.price},
            )

            if not created:
                item.quantity += quantity
                if item.quantity > product.stock:
                    raise ValueError(f"Only {product.stock} items available")
                item.save()

        self._invalidate_cart_cache(cart)
        return item

    def update_item_quantity(
        self,
        cart: Cart,
        item_id: int,
        quantity: int,
    ) -> Optional[CartItem]:
        """
        Update cart item quantity.

        Args:
            cart: The cart object
            item_id: The cart item ID
            quantity: New quantity

        Returns:
            Updated CartItem or None if not found

        Raises:
            ValueError: If insufficient stock
        """
        try:
            item = CartItem.objects.get(id=item_id, cart=cart)
        except CartItem.DoesNotExist:
            return None

        if quantity < 1:
            raise ValueError("Quantity must be at least 1")

        if quantity > item.product.stock:
            raise ValueError(f"Only {item.product.stock} items available")

        item.quantity = quantity
        item.save()

        self._invalidate_cart_cache(cart)
        return item

    def remove_item(self, cart: Cart, item_id: int) -> bool:
        """
        Remove item from cart.

        Args:
            cart: The cart object
            item_id: The cart item ID

        Returns:
            True if item was removed, False otherwise
        """
        deleted, _ = CartItem.objects.filter(id=item_id, cart=cart).delete()

        if deleted:
            self._invalidate_cart_cache(cart)

        return bool(deleted)

    def clear_cart(self, cart: Cart) -> None:
        """
        Remove all items from cart.

        Args:
            cart: The cart object
        """
        cart.items.all().delete()
        self._invalidate_cart_cache(cart)

    def merge_carts(self, source_cart: Cart, target_cart: Cart) -> Cart:
        """
        Merge source cart into target cart.

        Args:
            source_cart: Cart to merge from (will be deleted)
            target_cart: Cart to merge into

        Returns:
            The merged target cart
        """
        with transaction.atomic():
            for source_item in source_cart.items.all():
                target_item, created = CartItem.objects.get_or_create(
                    cart=target_cart,
                    product=source_item.product,
                    defaults={
                        "quantity": source_item.quantity,
                        "unit_price": source_item.unit_price,
                    },
                )

                if not created:
                    # Check stock limit
                    max_quantity = min(
                        target_item.quantity + source_item.quantity,
                        source_item.product.stock,
                    )
                    target_item.quantity = max_quantity
                    target_item.save()

            # Delete source cart
            source_cart.delete()

        self._invalidate_cart_cache(target_cart)
        return target_cart

    def get_cart_summary(self, cart: Cart) -> dict:
        """
        Get cart summary with totals.

        Args:
            cart: The cart object

        Returns:
            Dictionary with cart summary
        """
        items = cart.items.select_related("product").all()

        return {
            "items": [
                {
                    "id": item.id,
                    "product_id": item.product.id,
                    "product_name": item.product.name,
                    "product_slug": item.product.slug,
                    "product_image": item.product.main_image.url if item.product.main_image else "",
                    "price": float(item.unit_price),
                    "quantity": item.quantity,
                    "subtotal": float(item.subtotal),
                    "stock": item.product.stock,
                }
                for item in items
            ],
            "total_items": sum(item.quantity for item in items),
            "total_price": sum(float(item.subtotal) for item in items),
        }

    def _invalidate_cart_cache(self, cart: Cart) -> None:
        """Invalidate cart cache."""
        if cart.user:
            cache.delete(f"{self.cache_prefix}:user:{cart.user.id}")
        if cart.session_key:
            cache.delete(f"{self.cache_prefix}:session:{cart.session_key}")


class WishlistService:
    """Service for wishlist operations."""

    def __init__(self):
        self.cache_prefix = "wishlist"

    def get_or_create_wishlist(self, user_id: int, name: str = "My Wishlist") -> dict:
        """Get or create user's wishlist."""
        from backend.core.models import Wishlist

        wishlist, created = Wishlist.objects.get_or_create(
            user_id=user_id,
            is_default=True,
            defaults={"name": name}
        )

        return {
            "id": wishlist.id,
            "name": wishlist.name,
            "item_count": wishlist.item_count,
            "items": self._serialize_items(wishlist.items.all()),
        }

    def add_to_wishlist(self, user_id: int, product_id: int) -> dict:
        """Add product to wishlist."""
        from backend.core.models import Wishlist, Product

        # Validate product exists
        try:
            Product.objects.get(id=product_id, is_active=True)
        except Product.DoesNotExist:
            raise ValueError("Product not found or inactive")

        wishlist, _ = Wishlist.objects.get_or_create(
            user_id=user_id,
            is_default=True,
            defaults={"name": "My Wishlist"}
        )

        item = wishlist.add_product(product_id)
        return self._serialize_item(item)

    def remove_from_wishlist(self, user_id: int, product_id: int) -> bool:
        """Remove product from wishlist."""
        from backend.core.models import Wishlist

        wishlist, _ = Wishlist.objects.get_or_create(
            user_id=user_id,
            is_default=True,
            defaults={"name": "My Wishlist"}
        )

        return wishlist.remove_product(product_id)

    def move_to_cart(self, user_id: int, product_id: int, cart_id: int) -> bool:
        """Move product from wishlist to cart."""
        from backend.core.models import Wishlist

        wishlist, _ = Wishlist.objects.get_or_create(
            user_id=user_id,
            is_default=True,
            defaults={"name": "My Wishlist"}
        )

        return wishlist.move_to_cart(product_id, cart_id)

    def clear_wishlist(self, user_id: int) -> bool:
        """Clear all items from wishlist."""
        from backend.core.models import Wishlist

        wishlist, _ = Wishlist.objects.get_or_create(
            user_id=user_id,
            is_default=True,
            defaults={"name": "My Wishlist"}
        )

        wishlist.clear()
        return True

    def get_wishlist_items(self, user_id: int) -> list:
        """Get all items in wishlist."""
        from backend.core.models import Wishlist

        try:
            wishlist = Wishlist.objects.get(user_id=user_id, is_default=True)
            return self._serialize_items(wishlist.items.all())
        except Wishlist.DoesNotExist:
            return []

    def _serialize_items(self, items) -> list:
        """Serialize wishlist items."""
        return [self._serialize_item(item) for item in items]

    def _serialize_item(self, item) -> dict:
        """Serialize a single wishlist item."""
        return {
            "id": item.id,
            "product_id": item.product.id,
            "product_name": item.product.name,
            "product_slug": item.product.slug,
            "product_image": item.product.main_image.url if item.product.main_image else None,
            "price": float(item.product.price),
            "original_price": float(item.product.original_price) if item.product.original_price else None,
            "in_stock": item.is_in_stock,
            "on_sale": item.is_on_sale,
            "notes": item.notes,
            "created_at": item.created_at.isoformat(),
        }
