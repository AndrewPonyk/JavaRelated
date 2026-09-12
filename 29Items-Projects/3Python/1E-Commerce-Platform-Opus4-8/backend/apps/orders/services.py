"""Checkout orchestration (the synchronous critical path of ARCHITECTURE §2.3).

Keeps the user-blocking section minimal: validate -> reserve stock -> charge ->
persist order. Everything else (email, search signals, reco data) is fired
asynchronously *after commit* via the `order.placed` event.
"""

from __future__ import annotations

import uuid

from django.db import transaction

from apps.cart.models import Cart
from apps.inventory import services as inventory_services
from common.utils.exceptions import CartEmptyError, DomainError

from .models import Order, OrderItem
from .payments import get_gateway


class PaymentDeclinedError(DomainError):
    status_code = 402
    code = "PAYMENT_DECLINED"


def _generate_order_number() -> str:
    return f"ORD-{uuid.uuid4().hex[:12].upper()}"


@transaction.atomic
def place_order(*, user, cart: Cart, payment_token: str) -> Order:
    items = list(cart.items.select_related("product"))
    if not items:
        raise CartEmptyError("Cannot checkout an empty cart.")

    # Idempotency: a repeat checkout of the same cart returns the existing order
    # rather than reserving/charging again.
    idempotency_key = f"cart-{cart.id}"
    existing = Order.objects.filter(idempotency_key=idempotency_key).first()
    if existing:
        return existing

    # 1. Reserve stock atomically (raises 409 if insufficient).
    inventory_services.reserve(
        cart_id=cart.id,
        items=[(i.product_id, i.quantity) for i in items],
    )

    total = cart.total

    # 2. Charge the payment gateway with an idempotency key.
    result = get_gateway().charge(
        token=payment_token,
        amount=total,
        currency=items[0].product.currency,
        idempotency_key=idempotency_key,
    )
    if not result.success:
        raise PaymentDeclinedError("Payment was declined.", details={"reason": result.error})

    # 3. Persist the order + items.
    order = Order.objects.create(
        number=_generate_order_number(),
        user=user,
        status=Order.Status.PAID,
        total_amount=total,
        currency=items[0].product.currency,
        payment_intent_id=result.payment_intent_id,
        idempotency_key=idempotency_key,
    )
    OrderItem.objects.bulk_create(
        OrderItem(
            order=order,
            product=i.product,
            quantity=i.quantity,
            unit_price=i.unit_price,
        )
        for i in items
    )

    # 4. Commit reservations (permanent stock decrement) and close the cart.
    inventory_services.commit_reservations(cart_id=cart.id)
    cart.is_active = False
    cart.save(update_fields=["is_active"])

    # 5. Fire side effects only after the transaction commits.
    transaction.on_commit(lambda: _emit_order_placed(order.id))
    return order


def cancel_order(*, order: Order) -> Order:
    if order.status not in {Order.Status.PENDING, Order.Status.PAID}:
        raise DomainError(f"Cannot cancel an order in state '{order.status}'.")
    order.status = Order.Status.CANCELLED
    order.save(update_fields=["status"])
    # TODO(phase-3): trigger refund via payment gateway + restock.
    return order


def _emit_order_placed(order_id: int) -> None:
    from . import tasks

    tasks.send_order_confirmation_email.delay(order_id)
    tasks.capture_recommendation_signal.delay(order_id)
