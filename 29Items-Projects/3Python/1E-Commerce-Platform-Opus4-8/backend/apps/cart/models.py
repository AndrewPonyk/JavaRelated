"""Cart models — supports both authenticated and guest (session) carts."""

from __future__ import annotations

from django.conf import settings
from django.db import models


class Cart(models.Model):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.CASCADE,
        related_name="carts",
    )
    session_key = models.CharField(max_length=40, blank=True, db_index=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    @property
    def total(self):
        return sum((item.line_total for item in self.items.all()), start=0)

    def __str__(self) -> str:
        owner = self.user_id or f"guest:{self.session_key}"
        return f"Cart #{self.pk} ({owner})"


class CartItem(models.Model):
    cart = models.ForeignKey(Cart, on_delete=models.CASCADE, related_name="items")
    product = models.ForeignKey("catalog.Product", on_delete=models.PROTECT)
    quantity = models.PositiveIntegerField(default=1)
    # Price snapshot so later price changes don't mutate an in-flight cart.
    unit_price = models.DecimalField(max_digits=10, decimal_places=2)

    class Meta:
        unique_together = ("cart", "product")

    @property
    def line_total(self):
        return self.unit_price * self.quantity

    def __str__(self) -> str:
        return f"{self.quantity} x product#{self.product_id}"
