"""
Wishlist Models

Models for wishlist/saved items functionality.
"""

from django.db import models
from django.core.cache import cache


class Wishlist(models.Model):
    """
    User wishlist for saving products for later.
    """

    class Meta:
        db_table = "wishlists"
        verbose_name = "Wishlist"
        verbose_name_plural = "Wishlists"
        ordering = ["-created_at"]

    name = models.CharField(max_length=255, default="My Wishlist")
    user = models.ForeignKey(
        "User",
        on_delete=models.CASCADE,
        related_name="wishlists",
    )
    is_default = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"{self.name} ({self.user.email})"

    @property
    def item_count(self) -> int:
        """Get total number of items in wishlist."""
        return self.items.count()

    def add_product(self, product_id: int) -> "WishlistItem":
        """Add a product to the wishlist."""
        item, created = WishlistItem.objects.get_or_create(
            wishlist=self,
            product_id=product_id,
        )
        return item

    def remove_product(self, product_id: int) -> bool:
        """Remove a product from the wishlist."""
        deleted, _ = WishlistItem.objects.filter(
            wishlist=self,
            product_id=product_id,
        ).delete()
        return deleted > 0

    def has_product(self, product_id: int) -> bool:
        """Check if product is in wishlist."""
        return self.items.filter(product_id=product_id).exists()

    def move_to_cart(self, product_id: int, cart_id: int) -> bool:
        """Move product from wishlist to cart."""
        from backend.core.models import Cart, CartItem

        try:
            wishlist_item = self.items.get(product_id=product_id)

            # Add to cart
            cart = Cart.objects.get(id=cart_id)
            CartItem.objects.create(
                cart=cart,
                product_id=product_id,
                quantity=1,
                unit_price=wishlist_item.product.price,
            )

            # Remove from wishlist
            wishlist_item.delete()
            return True

        except (WishlistItem.DoesNotExist, Cart.DoesNotExist):
            return False

    def clear(self) -> None:
        """Remove all items from wishlist."""
        self.items.all().delete()

    def save(self, *args, **kwargs):
        """Override save to invalidate cache."""
        super().save(*args, **kwargs)
        cache.delete(f"wishlist:user:{self.user.id}")


class WishlistItem(models.Model):
    """
    Individual item in a wishlist.
    """

    class Meta:
        db_table = "wishlist_items"
        verbose_name = "Wishlist Item"
        verbose_name_plural = "Wishlist Items"
        unique_together = [["wishlist", "product"]]
        ordering = ["-created_at"]

    wishlist = models.ForeignKey(
        Wishlist,
        on_delete=models.CASCADE,
        related_name="items",
    )
    product = models.ForeignKey(
        "Product",
        on_delete=models.CASCADE,
    )
    notes = models.TextField(blank=True, null=True)
    priority = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"{self.product.name} in {self.wishlist.name}"

    @property
    def is_in_stock(self) -> bool:
        """Check if product is in stock."""
        return self.product.stock > 0

    @property
    def current_price(self) -> float:
        """Get current product price."""
        return float(self.product.price)

    @property
    def is_on_sale(self) -> bool:
        """Check if product is on sale."""
        return (
            self.product.original_price is not None
            and self.product.original_price > self.product.price
        )


class RecentlyViewed(models.Model):
    """
    Track recently viewed products for users.
    """

    class Meta:
        db_table = "recently_viewed"
        verbose_name = "Recently Viewed"
        verbose_name_plural = "Recently Viewed"
        ordering = ["-viewed_at"]
        unique_together = [["user", "product"]]

    user = models.ForeignKey(
        "User",
        on_delete=models.CASCADE,
        related_name="recently_viewed",
    )
    product = models.ForeignKey(
        "Product",
        on_delete=models.CASCADE,
    )
    viewed_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"{self.user.email} viewed {self.product.name}"

    @classmethod
    def add_view(cls, user_id: int, product_id: int) -> None:
        """Add or update a recently viewed item."""
        item, created = cls.objects.get_or_create(
            user_id=user_id,
            product_id=product_id,
        )
        if not created:
            item.save()  # Update viewed_at

    @classmethod
    def get_recent_products(cls, user_id: int, limit: int = 10):
        """Get recently viewed products for a user."""
        from backend.core.models import Product

        product_ids = cls.objects.filter(
            user_id=user_id,
        ).values_list("product_id", flat=True)[:limit]

        return Product.objects.filter(id__in=product_ids)

    @classmethod
    def clear_old_views(cls, user_id: int, keep_last: int = 50) -> int:
        """Clear old recently viewed items, keeping only the most recent."""
        views = cls.objects.filter(user_id=user_id)
        total = views.count()

        if total > keep_last:
            # Get IDs of items to keep
            keep_ids = views.values_list("id", flat=True)[:keep_last]

            # Delete the rest
            deleted, _ = cls.objects.filter(user_id=user_id).exclude(id__in=keep_ids).delete()
            return deleted

        return 0
