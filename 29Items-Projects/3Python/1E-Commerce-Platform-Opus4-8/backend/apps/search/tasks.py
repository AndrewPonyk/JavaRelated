"""Async Elasticsearch indexing tasks (keep ES eventually consistent with PG)."""

from __future__ import annotations

import logging

from celery import shared_task
from django.conf import settings

logger = logging.getLogger(__name__)


@shared_task(bind=True, max_retries=3, retry_backoff=True, ignore_result=True)
def index_product(self, product_id: int) -> None:
    from apps.catalog.models import Product

    from .client import get_client

    try:
        product = Product.objects.select_related("category", "vendor").get(id=product_id)
    except Product.DoesNotExist:
        return  # Deleted before indexing ran; nothing to do.

    get_client().index(
        index=settings.ELASTICSEARCH_PRODUCT_INDEX,
        id=str(product.id),
        document={
            "name": product.name,
            "description": product.description,
            "sku": product.sku,
            "category": product.category.slug,
            "vendor": product.vendor.slug,
            "price": float(product.price),
            "status": product.status,
        },
    )


@shared_task(ignore_result=True)
def remove_product(product_id: int) -> None:
    from .client import get_client

    # ES 8.x removed the `ignore=` kwarg; tolerate a missing doc via options().
    get_client().options(ignore_status=404).delete(
        index=settings.ELASTICSEARCH_PRODUCT_INDEX, id=str(product_id)
    )


@shared_task(ignore_result=True)
def full_reindex() -> int:
    """Periodic safety net: reconcile the whole index from PostgreSQL."""
    from apps.catalog.models import Product

    from .client import ensure_index

    ensure_index()
    count = 0
    for product in Product.objects.filter(status=Product.Status.ACTIVE).iterator():
        index_product.delay(product.id)
        count += 1
    logger.info("search.reindex.enqueued", extra={"count": count})
    return count
