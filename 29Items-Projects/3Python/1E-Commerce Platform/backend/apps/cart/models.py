"""Cart models for E-Commerce Platform."""
import uuid
from decimal import Decimal

from django.conf import settings
from django.core.validators import MinValueValidator
from django.db import models


class Cart(models.Model):
    """Shopping cart model supporting both authenticated and guest users."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)

    # User association (null for guest carts)
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        related_name='cart'
    )

    # Session key for guest carts
    session_key = models.CharField(max_length=40, null=True, blank=True, db_index=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'cart'
        verbose_name_plural = 'carts'

    def __str__(self):
        if self.user:
            return f"Cart for {self.user.email}"
        return f"Guest Cart {self.session_key[:8]}..."

    @property
    def item_count(self) -> int:
        """Return total number of items in cart."""
        return sum(item.quantity for item in self.items.all())

    @property
    def subtotal(self) -> Decimal:
        """Calculate cart subtotal before discounts and shipping."""
        return sum(item.line_total for item in self.items.all())

    @property
    def is_empty(self) -> bool:
        """Check if cart has no items."""
        return self.items.count() == 0


class CartItem(models.Model):
    """Individual item in a shopping cart."""

    cart = models.ForeignKey(
        Cart,
        on_delete=models.CASCADE,
        related_name='items'
    )
    product = models.ForeignKey(
        'products.Product',
        on_delete=models.CASCADE
    )
    variant = models.ForeignKey(
        'products.ProductVariant',
        on_delete=models.CASCADE,
        null=True,
        blank=True
    )
    quantity = models.PositiveIntegerField(
        default=1,
        validators=[MinValueValidator(1)]
    )

    # Price snapshot at time of adding to cart
    unit_price = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        validators=[MinValueValidator(Decimal('0.01'))]
    )

    # Timestamps
    added_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'cart item'
        verbose_name_plural = 'cart items'
        unique_together = ['cart', 'product', 'variant']

    def __str__(self):
        return f"{self.quantity}x {self.product.name}"

    @property
    def line_total(self) -> Decimal:
        """Calculate total for this line item."""
        return self.unit_price * self.quantity

    def save(self, *args, **kwargs):
        """Set unit price from product/variant if not set."""
        if not self.unit_price:
            if self.variant:
                self.unit_price = self.variant.effective_price
            else:
                self.unit_price = self.product.price
        super().save(*args, **kwargs)
