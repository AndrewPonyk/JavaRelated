"""
Inventory Tasks

Celery tasks for inventory management.
"""

from celery import shared_task
from django.db import transaction
import logging

logger = logging.getLogger("backend")


@shared_task
def sync_inventory() -> dict:
    """
    Sync inventory with external systems (scheduled task).

    Returns:
        Dictionary with sync results
    """
    from backend.core.models import Product, InventoryItem
    from django.core.cache import cache

    synced = 0
    errors = 0

    # Sync stock levels from InventoryItem to Product
    try:
        inventory_items = InventoryItem.objects.select_related("product").all()

        for item in inventory_items:
            try:
                # Update product stock from warehouse inventory
                current_stock = item.product.stock
                warehouse_stock = item.available_quantity

                if current_stock != warehouse_stock:
                    item.product.stock = warehouse_stock
                    item.product.save(update_fields=["stock"])
                    synced += 1

                # Invalidate cache
                cache.delete(f"product:{item.product_id}")

            except Exception as e:
                logger.error(f"Failed to sync product {item.product_id}: {e}")
                errors += 1

        logger.info(f"Inventory sync completed: {synced} synced, {errors} errors")

    except Exception as e:
        logger.error(f"Inventory sync failed: {e}")
        errors += 1

    return {
        "synced": synced,
        "errors": errors,
    }


@shared_task(bind=True)
def reserve_inventory(self, product_id: int, quantity: int) -> bool:
    """
    Reserve inventory for an order.

    Args:
        product_id: The product ID
        quantity: Quantity to reserve

    Returns:
        True if reserved successfully
    """
    from backend.core.models import Product

    try:
        with transaction.atomic():
            product = Product.objects.select_for_update().get(id=product_id)

            if product.stock < quantity:
                return False

            product.stock -= quantity
            product.save(update_fields=["stock"])

        logger.info(f"Reserved {quantity} units of product {product_id}")
        return True

    except Exception as e:
        logger.error(f"Failed to reserve inventory for product {product_id}: {e}")
        return False


@shared_task(bind=True)
def release_inventory(self, product_id: int, quantity: int) -> bool:
    """
    Release reserved inventory (e.g., when order is cancelled).

    Args:
        product_id: The product ID
        quantity: Quantity to release

    Returns:
        True if released successfully
    """
    from backend.core.models import Product

    try:
        with transaction.atomic():
            product = Product.objects.select_for_update().get(id=product_id)
            product.stock += quantity
            product.save(update_fields=["stock"])

        logger.info(f"Released {quantity} units of product {product_id}")
        return True

    except Exception as e:
        logger.error(f"Failed to release inventory for product {product_id}: {e}")
        return False


@shared_task
def check_low_stock() -> list:
    """
    Check for low stock items and send alerts.

    Returns:
        List of low stock products
    """
    from backend.core.models import Product

    low_stock_threshold = 10
    low_stock_products = list(
        Product.objects.select_related("vendor").filter(
            is_active=True,
            stock__lte=low_stock_threshold,
        )
    )

    if low_stock_products:
        logger.warning(f"Low stock alert: {len(low_stock_products)} products")

        # Group by vendor and send alerts
        from collections import defaultdict
        vendor_products = defaultdict(list)

        for product in low_stock_products:
            if product.vendor:
                vendor_products[product.vendor].append(product)

        # Send email to each vendor
        for vendor, products in vendor_products.items():
            send_low_stock_alert_to_vendor.delay(vendor.id, [p.id for p in products])

    return [{"id": p.id, "name": p.name, "stock": p.stock} for p in low_stock_products]


@shared_task
def send_low_stock_alert_to_vendor(vendor_id: int, product_ids: list) -> bool:
    """
    Send low stock alert email to vendor.

    Args:
        vendor_id: The vendor ID
        product_ids: List of product IDs with low stock

    Returns:
        True if email sent successfully
    """
    from backend.core.models import Vendor, Product
    from django.core.mail import send_mail
    from django.conf import settings

    try:
        vendor = Vendor.objects.get(id=vendor_id)
        products = Product.objects.filter(id__in=product_ids)

        subject = f"Low Stock Alert - {vendor.name}"

        product_list = "\n".join([
            f"- {p.name}: {p.stock} units remaining"
            for p in products
        ])

        message = f"""
        The following products are running low on stock:

        {product_list}

        Please restock soon to avoid stockouts.

        Visit your vendor dashboard to update inventory.
        """

        send_mail(
            subject=subject,
            message=message,
            from_email=getattr(settings, "DEFAULT_FROM_EMAIL", "noreply@example.com"),
            recipient_list=[vendor.email],
            fail_silently=True,
        )

        logger.info(f"Low stock alert sent to vendor {vendor.email}")
        return True

    except Exception as e:
        logger.error(f"Failed to send low stock alert to vendor {vendor_id}: {e}")
        return False
