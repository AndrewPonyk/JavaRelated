"""
Order Tasks

Celery tasks for order processing.
"""

from celery import shared_task
import logging

logger = logging.getLogger("backend")


@shared_task
def process_order_confirmation(order_id: str) -> bool:
    """
    Process tasks after order confirmation.

    Args:
        order_id: The order ID

    Returns:
        True if processed successfully
    """
    from backend.core.models import Order
    from backend.tasks.inventory_tasks import reserve_inventory

    try:
        order = Order.objects.select_related("user").prefetch_related("items__product").get(id=order_id)

        # Send confirmation email
        from backend.tasks.email_tasks import send_order_confirmation_email
        send_order_confirmation_email.delay(order_id)

        # Reserve inventory for each item
        for item in order.items.all():
            reserve_inventory.delay(item.product_id, item.quantity)

        # Send vendor notifications
        for item in order.items.all():
            if item.product.vendor:
                notify_vendor_of_order.delay(item.product.vendor.id, order_id, item.id)

        # Update analytics
        update_analytics_for_order.delay(order_id)

        logger.info(f"Order confirmation processed for order {order_id}")
        return True

    except Exception as e:
        logger.error(f"Failed to process order confirmation for {order_id}: {e}")
        return False


@shared_task
def notify_vendor_of_order(vendor_id: int, order_id: str, order_item_id: int) -> bool:
    """
    Send notification to vendor about new order.

    Args:
        vendor_id: The vendor ID
        order_id: The order ID
        order_item_id: The order item ID

    Returns:
        True if notification sent successfully
    """
    from backend.core.models import Vendor, Order, OrderItem

    try:
        vendor = Vendor.objects.get(id=vendor_id)
        order = Order.objects.get(id=order_id)
        item = OrderItem.objects.get(id=order_item_id)

        # Send email notification
        from django.core.mail import send_mail
        from django.conf import settings

        subject = f"New Order Received - {order.order_number}"
        message = f"""
        You have received a new order for {item.product.name}.

        Order Number: {order.order_number}
        Quantity: {item.quantity}
        Unit Price: ${item.unit_price}
        Total: ${item.subtotal}

        Please ship the item to:
        {order.shipping_address.get('first_name', '')} {order.shipping_address.get('last_name', '')}
        {order.shipping_address.get('street', '')}
        {order.shipping_address.get('city', '')}, {order.shipping_address.get('state', '')} {order.shipping_address.get('zip_code', '')}
        """

        send_mail(
            subject=subject,
            message=message,
            from_email=getattr(settings, "DEFAULT_FROM_EMAIL", "noreply@example.com"),
            recipient_list=[vendor.email],
            fail_silently=True,
        )

        logger.info(f"Vendor notification sent to {vendor.email} for order {order_id}")
        return True

    except Exception as e:
        logger.error(f"Failed to send vendor notification for order {order_id}: {e}")
        return False


@shared_task
def update_analytics_for_order(order_id: str) -> bool:
    """
    Update analytics for a new order.

    Args:
        order_id: The order ID

    Returns:
        True if updated successfully
    """
    from backend.core.models import Order
    from django.core.cache import cache

    try:
        order = Order.objects.get(id=order_id)

        # Update daily order count cache
        from django.utils import timezone
        today = timezone.now().date()

        cache_key = f"analytics:orders:{today}"
        daily_count = cache.get(cache_key, 0)
        cache.set(cache_key, daily_count + 1, timeout=86400)  # 24 hours

        # Update daily revenue cache
        revenue_key = f"analytics:revenue:{today}"
        daily_revenue = cache.get(revenue_key, 0)
        cache.set(revenue_key, daily_revenue + float(order.total), timeout=86400)

        logger.info(f"Analytics updated for order {order_id}")
        return True

    except Exception as e:
        logger.error(f"Failed to update analytics for order {order_id}: {e}")
        return False


@shared_task
def cancel_expired_orders() -> int:
    """
    Cancel orders that have been pending too long.

    Scheduled task to run periodically.

    Returns:
        Number of orders cancelled
    """
    from django.utils import timezone
    from datetime import timedelta
    from backend.core.models import Order, OrderStatus

    # Cancel orders pending for more than 30 minutes
    expiry_time = timezone.now() - timedelta(minutes=30)
    expired_orders = Order.objects.filter(
        status=OrderStatus.PENDING,
        created_at__lt=expiry_time,
    )

    count = 0
    for order in expired_orders:
        try:
            order.cancel()
            count += 1
        except Exception as e:
            logger.error(f"Failed to cancel expired order {order.id}: {e}")

    if count > 0:
        logger.info(f"Cancelled {count} expired orders")

    return count


@shared_task
def update_order_statistics() -> dict:
    """
    Update order statistics for analytics.

    Scheduled task to run periodically.

    Returns:
        Dictionary with statistics
    """
    from backend.core.models import Order, OrderStatus
    from django.db.models import Count, Sum, Avg
    from django.utils import timezone
    from datetime import timedelta

    today = timezone.now().date()
    yesterday = today - timedelta(days=1)
    this_month = today.replace(day=1)

    stats = {
        "today": Order.objects.filter(created_at__date=today).count(),
        "yesterday": Order.objects.filter(created_at__date=yesterday).count(),
        "this_month": Order.objects.filter(created_at__gte=this_month).count(),
        "total": Order.objects.count(),
        "status_breakdown": {},
        "total_revenue": 0,
    }

    # Status breakdown
    for status, label in OrderStatus.choices:
        stats["status_breakdown"][status] = Order.objects.filter(
            status=status
        ).count()

    # Total revenue (paid orders only)
    revenue_result = Order.objects.filter(
        payment_status="paid"
    ).aggregate(total=Sum("total"))

    stats["total_revenue"] = revenue_result["total"] or 0

    logger.info(f"Order statistics updated: {stats}")
    return stats
