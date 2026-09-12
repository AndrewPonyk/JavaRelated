"""Order async tasks. Must be idempotent — Celery delivers at-least-once."""

from __future__ import annotations

import logging

from celery import shared_task
from django.conf import settings
from django.core.mail import send_mail
from django.db import transaction
from django.utils import timezone

logger = logging.getLogger(__name__)


@shared_task(
    bind=True,
    max_retries=5,
    default_retry_delay=30,
    retry_backoff=True,
    retry_jitter=True,
    ignore_result=True,
)
def send_order_confirmation_email(self, order_id: int) -> None:
    """Send the confirmation email exactly once (idempotent via timestamp)."""
    from .models import Order

    with transaction.atomic():
        try:
            order = Order.objects.select_for_update().select_related("user").get(id=order_id)
        except Order.DoesNotExist:
            logger.warning("order.email.missing", extra={"order_id": order_id})
            return

        if order.confirmation_email_sent_at is not None:
            return  # Already sent — at-least-once delivery guard.

        lines = "\n".join(
            f"  - {it.quantity} x {it.product.name} @ {it.unit_price}"
            for it in order.items.select_related("product")
        )
        body = (
            f"Thanks for your order {order.number}!\n\n"
            f"{lines}\n\n"
            f"Total: {order.currency} {order.total_amount}\n"
        )
        try:
            send_mail(
                subject=f"Order confirmation {order.number}",
                message=body,
                from_email=settings.DEFAULT_FROM_EMAIL,
                recipient_list=[order.user.email],
                fail_silently=False,
            )
        except Exception as exc:  # transient SMTP failure -> retry with backoff
            logger.error("order.email.failed", extra={"order_id": order_id})
            raise self.retry(exc=exc) from exc

        order.confirmation_email_sent_at = timezone.now()
        order.save(update_fields=["confirmation_email_sent_at"])
    logger.info("order.email.sent", extra={"order_id": order_id, "number": order.number})


@shared_task(ignore_result=True)
def capture_recommendation_signal(order_id: int) -> None:
    """Record purchase interactions to feed the recommender's training set."""
    from apps.recommendations.models import Interaction

    from .models import Order

    try:
        order = Order.objects.prefetch_related("items").get(id=order_id)
    except Order.DoesNotExist:
        return

    Interaction.objects.bulk_create(
        [
            Interaction(
                user_id=order.user_id,
                product_id=item.product_id,
                event=Interaction.Event.PURCHASE,
                weight=float(item.quantity),
            )
            for item in order.items.all()
        ]
    )
