"""Inventory models — stock level per product + time-boxed reservations."""

from __future__ import annotations

from django.db import models


class StockItem(models.Model):
    product = models.OneToOneField(
        "catalog.Product", on_delete=models.CASCADE, related_name="stock"
    )
    quantity_on_hand = models.PositiveIntegerField(default=0)
    quantity_reserved = models.PositiveIntegerField(default=0)

    @property
    def quantity_available(self) -> int:
        return self.quantity_on_hand - self.quantity_reserved

    def __str__(self) -> str:
        return f"{self.product_id}: {self.quantity_available} available"


class Reservation(models.Model):
    """A short-lived hold placed on stock while a cart heads to checkout."""

    stock_item = models.ForeignKey(StockItem, on_delete=models.CASCADE, related_name="reservations")
    cart_id = models.BigIntegerField(db_index=True)
    quantity = models.PositiveIntegerField()
    expires_at = models.DateTimeField(db_index=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"Reservation(cart={self.cart_id}, qty={self.quantity})"
