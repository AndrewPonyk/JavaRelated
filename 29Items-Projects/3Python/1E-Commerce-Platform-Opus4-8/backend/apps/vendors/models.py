"""Vendor model — a seller that owns products and receives payouts."""

from __future__ import annotations

from django.conf import settings
from django.db import models
from django.utils.text import slugify


class Vendor(models.Model):
    class Status(models.TextChoices):
        PENDING = "pending", "Pending review"
        APPROVED = "approved", "Approved"
        SUSPENDED = "suspended", "Suspended"

    owner = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="vendor_profile",
    )
    name = models.CharField(max_length=200)
    slug = models.SlugField(max_length=220, unique=True, blank=True)
    status = models.CharField(max_length=12, choices=Status.choices, default=Status.PENDING)
    # Payout account reference (e.g. Stripe Connect account id).
    payout_account_id = models.CharField(max_length=255, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    @property
    def is_active(self) -> bool:
        return self.status == self.Status.APPROVED

    def save(self, *args, **kwargs) -> None:
        if not self.slug:
            base = slugify(self.name) or "vendor"
            slug = base
            i = 1
            while Vendor.objects.filter(slug=slug).exclude(pk=self.pk).exists():
                i += 1
                slug = f"{base}-{i}"
            self.slug = slug
        super().save(*args, **kwargs)

    def __str__(self) -> str:
        return self.name
