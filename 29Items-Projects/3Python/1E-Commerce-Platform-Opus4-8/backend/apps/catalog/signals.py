"""Keep Elasticsearch eventually consistent with the catalog.

On product save/delete we enqueue an async indexing task (never index inline —
see TECH-NOTES §3.6). Uses transaction.on_commit so we only index rows that
were actually committed. Disabled when SEARCH_INDEXING_ENABLED is False.
"""

from __future__ import annotations

from django.conf import settings
from django.db import transaction
from django.db.models.signals import post_delete, post_save
from django.dispatch import receiver

from .models import Product


@receiver(post_save, sender=Product)
def index_product_on_save(sender, instance: Product, **kwargs) -> None:
    if not settings.SEARCH_INDEXING_ENABLED:
        return
    from apps.search import tasks

    transaction.on_commit(lambda: tasks.index_product.delay(instance.id))


@receiver(post_delete, sender=Product)
def remove_product_from_index(sender, instance: Product, **kwargs) -> None:
    if not settings.SEARCH_INDEXING_ENABLED:
        return
    from apps.search import tasks

    transaction.on_commit(lambda: tasks.remove_product.delay(instance.id))
