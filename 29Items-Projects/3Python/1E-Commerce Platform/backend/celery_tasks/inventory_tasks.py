"""Inventory-related Celery tasks."""
from celery import shared_task
from django.db import transaction


@shared_task(bind=True, max_retries=3, soft_time_limit=300, acks_late=True)
def update_inventory_after_order(self, order_id: str):
    """Deduct inventory after order confirmation."""
    from apps.checkout.models import Order, OrderItem
    from apps.inventory.services import InventoryService

    try:
        order = Order.objects.get(id=order_id)

        with transaction.atomic():
            for item in order.items.all():
                InventoryService.deduct_stock(
                    product=item.product,
                    variant=item.variant,
                    quantity=item.quantity,
                    reference_type='order',
                    reference_id=str(order.id)
                )

    except Order.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=300, acks_late=True)
def restore_inventory_after_cancellation(self, order_id: str):
    """Restore inventory after order cancellation."""
    from apps.checkout.models import Order
    from apps.inventory.services import InventoryService

    try:
        order = Order.objects.get(id=order_id)

        with transaction.atomic():
            for item in order.items.all():
                InventoryService.add_stock(
                    product=item.product,
                    variant=item.variant,
                    quantity=item.quantity,
                    notes=f'Restored from cancelled order {order.order_number}'
                )

    except Order.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, soft_time_limit=600)
def check_low_stock_alerts(self):
    """Periodic task to check for low stock and create alerts."""
    from django.db.models import F
    from apps.inventory.models import InventoryItem, StockAlert
    from celery_tasks.email_tasks import send_low_stock_alert_email

    low_stock_items = InventoryItem.objects.filter(
        quantity_available__lte=F('reorder_point')
    ).exclude(
        alerts__status=StockAlert.AlertStatus.ACTIVE
    )

    for item in low_stock_items:
        alert = StockAlert.objects.create(
            inventory_item=item,
            threshold_triggered=item.reorder_point,
            current_quantity=item.quantity_available
        )
        # Send notification email
        send_low_stock_alert_email.delay(alert.id)


@shared_task(bind=True, soft_time_limit=3600)
def generate_inventory_report(self, vendor_id: int = None):
    """Generate inventory report for a vendor or all vendors."""
    from apps.inventory.models import InventoryItem
    from django.db.models import Sum

    queryset = InventoryItem.objects.all()
    if vendor_id:
        queryset = queryset.filter(product__vendor_id=vendor_id)

    report = {
        'total_products': queryset.count(),
        'total_stock_value': 0,  # TODO: Calculate based on cost price
        'low_stock_count': queryset.filter(
            quantity_available__lte=F('reorder_point')
        ).count(),
        'out_of_stock_count': queryset.filter(quantity_available=0).count(),
        'total_quantity': queryset.aggregate(Sum('quantity_on_hand'))['quantity_on_hand__sum'] or 0,
    }

    # TODO: Store report or send email
    return report
