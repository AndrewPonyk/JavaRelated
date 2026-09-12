"""Inventory background tasks."""

from __future__ import annotations

from celery import shared_task
from django.db import transaction
from django.utils import timezone


@shared_task(ignore_result=True)
def release_expired_reservations() -> int:
    """Periodic (every 5 min) release of stock held by abandoned carts.

    Returns the number of reservations released (useful for metrics/tests).
    """
    from .models import Reservation

    released = 0
    expired = Reservation.objects.filter(expires_at__lt=timezone.now())
    with transaction.atomic():
        for res in expired.select_for_update().select_related("stock_item"):
            stock = res.stock_item
            stock.quantity_reserved -= res.quantity
            stock.save(update_fields=["quantity_reserved"])
            res.delete()
            released += 1
    return released
