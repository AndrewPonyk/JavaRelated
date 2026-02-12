"""
Cart Models

Models for shopping cart functionality.
"""

from django.db import models
from django.core.cache import cache


class Cart(models.Model):
    """
    Shopping cart for users and guests.
    """

    class Meta:
        db_table = "carts"
        verbose_name = "Cart"
        verbose_name_plural = "Carts"

    # Associate with user if logged in
    user = models.OneToOneField(
        "User",
        on_delete=models.CASCADE,
        related_name="cart",
        null=True,
        blank=True,
    )

    # For guest carts (session-based)
    session_key = models.CharField(max_length=255, unique=True, null=True, blank=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    # Email tracking
    last_reminder_sent = models.DateTimeField(null=True, blank=True)

    def __str__(self) -> str:
        if self.user:
            return f"Cart for {self.user.email}"
        return f"Guest cart ({self.session_key})"

    @property
    def total_items(self) -> int:
        """Get total number of items in cart."""
        return sum(item.quantity for item in self.items.all())

    @property
    def total_price(self) -> float:
        """Get total price of all items in cart."""
        return sum(item.subtotal for item in self.items.all())

    def add_item(self, product_id: int, quantity: int = 1) -> "CartItem":
        """Add an item to the cart."""
        item, created = CartItem.objects.get_or_create(
            cart=self,
            product_id=product_id,
            defaults={"quantity": quantity},
        )

        if not created:
            item.quantity += quantity
            item.save()

        return item

    def remove_item(self, product_id: int) -> None:
        """Remove an item from the cart."""
        CartItem.objects.filter(cart=self, product_id=product_id).delete()

    def clear(self) -> None:
        """Remove all items from the cart."""
        self.items.all().delete()

    def save(self, *args, **kwargs):
        """Override save to invalidate cache."""
        super().save(*args, **kwargs)

        # Invalidate cart cache
        if self.user:
            cache.delete(f"cart:user:{self.user.id}")
        if self.session_key:
            cache.delete(f"cart:session:{self.session_key}")


class CartItem(models.Model):
    """
    Individual item in a shopping cart.
    """

    class Meta:
        db_table = "cart_items"
        verbose_name = "Cart Item"
        verbose_name_plural = "Cart Items"
        unique_together = [["cart", "product"]]
        ordering = ["-created_at"]

    cart = models.ForeignKey(
        Cart,
        on_delete=models.CASCADE,
        related_name="items",
    )
    product = models.ForeignKey(
        "Product",
        on_delete=models.CASCADE,
    )
    quantity = models.PositiveIntegerField(default=1)

    # Snapshot of product data at time of adding to cart
    unit_price = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        help_text="Price snapshot when added to cart",
    )

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"{self.product.name} x {self.quantity}"

    @property
    def subtotal(self) -> float:
        """Calculate subtotal for this cart item."""
        return float(self.unit_price) * self.quantity

    def save(self, *args, **kwargs):
        """Override save to set unit price from product."""
        if not self.pk and not self.unit_price:
            self.unit_price = self.product.price
        super().save(*args, **kwargs)
