"""Inventory service layer — the atomic stock-reservation core.

This is the consistency-critical section referenced in ARCHITECTURE.md §2.3.
`SELECT ... FOR UPDATE` + a DB transaction guarantee two concurrent checkouts
cannot oversell the same unit.
"""

from __future__ import annotations

from datetime import timedelta

from django.db import transaction
from django.utils import timezone

from common.utils.exceptions import InventoryInsufficientError

from .models import Reservation, StockItem

RESERVATION_TTL = timedelta(minutes=15)


@transaction.atomic
def reserve(*, cart_id: int, items: list[tuple[int, int]]) -> list[Reservation]:
    """Atomically reserve stock for (product_id, quantity) pairs.

    Raises InventoryInsufficientError (HTTP 409) if any line cannot be covered;
    the surrounding transaction then rolls back, holding nothing.
    """
    reservations: list[Reservation] = []
    expires_at = timezone.now() + RESERVATION_TTL

    for product_id, quantity in items:
        stock = StockItem.objects.select_for_update().get(product_id=product_id)
        if stock.quantity_available < quantity:
            raise InventoryInsufficientError(
                "Requested quantity exceeds available stock.",
                details={
                    "product_id": product_id,
                    "requested": quantity,
                    "available": stock.quantity_available,
                },
            )
        stock.quantity_reserved += quantity
        stock.save(update_fields=["quantity_reserved"])
        reservations.append(
            Reservation.objects.create(
                stock_item=stock,
                cart_id=cart_id,
                quantity=quantity,
                expires_at=expires_at,
            )
        )
    return reservations


@transaction.atomic
def commit_reservations(*, cart_id: int) -> None:
    """Convert reservations into a permanent stock decrement (order confirmed)."""
    reservations = Reservation.objects.select_related("stock_item").filter(cart_id=cart_id)
    for res in reservations.select_for_update():
        stock = res.stock_item
        stock.quantity_on_hand -= res.quantity
        stock.quantity_reserved -= res.quantity
        stock.save(update_fields=["quantity_on_hand", "quantity_reserved"])
        res.delete()
