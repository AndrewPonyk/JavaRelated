"""Interaction events — the implicit-feedback signal the recommender trains on."""

from __future__ import annotations

from django.conf import settings
from django.db import models


class Interaction(models.Model):
    class Event(models.TextChoices):
        VIEW = "view", "View"
        ADD_TO_CART = "add_to_cart", "Add to cart"
        PURCHASE = "purchase", "Purchase"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="interactions"
    )
    product = models.ForeignKey(
        "catalog.Product", on_delete=models.CASCADE, related_name="interactions"
    )
    event = models.CharField(max_length=16, choices=Event.choices)
    weight = models.FloatField(default=1.0)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        indexes = [models.Index(fields=["user", "product"])]

    def __str__(self) -> str:
        return f"{self.event} user#{self.user_id} product#{self.product_id}"
