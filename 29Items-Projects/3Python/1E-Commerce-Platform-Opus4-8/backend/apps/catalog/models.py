"""Catalog domain models — Category and Product.

Example database schema (Deliverable 4.3). Demonstrates relationships:
  Category 1──* Product *──1 Vendor
"""

from __future__ import annotations

from decimal import Decimal

from django.db import models
from django.utils.text import slugify


class TimeStampedModel(models.Model):
    """Abstract base adding created/updated timestamps to every model."""

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True


class Category(TimeStampedModel):
    name = models.CharField(max_length=120, unique=True)
    slug = models.SlugField(max_length=140, unique=True, blank=True)
    parent = models.ForeignKey(
        "self",
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="children",
    )

    class Meta:
        verbose_name_plural = "categories"
        ordering = ["name"]

    def save(self, *args, **kwargs) -> None:
        if not self.slug:
            self.slug = slugify(self.name)
        super().save(*args, **kwargs)

    def __str__(self) -> str:
        return self.name


class Product(TimeStampedModel):
    class Status(models.TextChoices):
        DRAFT = "draft", "Draft"
        ACTIVE = "active", "Active"
        ARCHIVED = "archived", "Archived"

    sku = models.CharField(max_length=64, unique=True, db_index=True)
    name = models.CharField(max_length=255)
    slug = models.SlugField(max_length=275, unique=True, blank=True)
    description = models.TextField(blank=True)
    price = models.DecimalField(max_digits=10, decimal_places=2)
    currency = models.CharField(max_length=3, default="USD")
    status = models.CharField(max_length=10, choices=Status.choices, default=Status.DRAFT)

    category = models.ForeignKey(Category, on_delete=models.PROTECT, related_name="products")
    vendor = models.ForeignKey("vendors.Vendor", on_delete=models.CASCADE, related_name="products")

    class Meta:
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["status", "category"]),
            models.Index(fields=["vendor", "status"]),
        ]
        constraints = [
            models.CheckConstraint(
                check=models.Q(price__gte=Decimal("0")),
                name="product_price_non_negative",
            ),
        ]

    def save(self, *args, **kwargs) -> None:
        if not self.slug:
            self.slug = slugify(f"{self.name}-{self.sku}")
        super().save(*args, **kwargs)

    @property
    def is_purchasable(self) -> bool:
        return self.status == self.Status.ACTIVE

    def __str__(self) -> str:
        return f"{self.name} ({self.sku})"
