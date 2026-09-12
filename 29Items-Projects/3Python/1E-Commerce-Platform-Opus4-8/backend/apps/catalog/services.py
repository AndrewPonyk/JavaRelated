"""Catalog service layer.

Business logic lives here, not in views. Views orchestrate HTTP; services
own domain rules and are independently unit-testable (no request objects).
This is the only place other apps should reach catalog behaviour.
"""

from __future__ import annotations

from django.core.cache import cache
from django.db import transaction
from django.db.models import QuerySet

from .models import Product

ACTIVE_PRODUCTS_CACHE_KEY = "catalog:active_products:v1"
CACHE_TTL_SECONDS = 300


def list_active_products() -> QuerySet[Product]:
    """Return purchasable products, with related rows prefetched (avoids N+1)."""
    return Product.objects.filter(status=Product.Status.ACTIVE).select_related(
        "category", "vendor", "stock"
    )


def get_product_by_slug(slug: str) -> Product:
    return Product.objects.select_related("category", "vendor", "stock").get(slug=slug)


@transaction.atomic
def create_product(*, vendor, validated_data: dict) -> Product:
    """Create a product (+ its stock row) and invalidate the active-list cache."""
    from apps.inventory.models import StockItem

    product = Product.objects.create(vendor=vendor, **validated_data)
    StockItem.objects.get_or_create(product=product)
    cache.delete(ACTIVE_PRODUCTS_CACHE_KEY)
    # Elasticsearch indexing is handled asynchronously via the post_save signal.
    return product


def archive_product(product: Product) -> Product:
    product.status = Product.Status.ARCHIVED
    product.save(update_fields=["status", "updated_at"])
    cache.delete(ACTIVE_PRODUCTS_CACHE_KEY)
    return product
