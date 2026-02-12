"""
Order Models

Models for orders, order items, and order status tracking.
"""

from django.db import models
from django.utils import timezone


class OrderStatus(models.TextChoices):
    """Order status choices."""

    PENDING = "pending", "Pending"
    CONFIRMED = "confirmed", "Confirmed"
    PROCESSING = "processing", "Processing"
    SHIPPED = "shipped", "Shipped"
    DELIVERED = "delivered", "Delivered"
    CANCELLED = "cancelled", "Cancelled"
    REFUNDED = "refunded", "Refunded"


class Order(models.Model):
    """
    Order model for the e-commerce platform.
    """

    class Meta:
        db_table = "orders"
        verbose_name = "Order"
        verbose_name_plural = "Orders"
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["order_number"]),
            models.Index(fields=["user", "-created_at"]),
            models.Index(fields=["status"]),
            models.Index(fields=["-created_at"]),
        ]

    # Order identification
    order_number = models.CharField(unique=True, max_length=50, editable=False)
    user = models.ForeignKey(
        "User",
        on_delete=models.SET_NULL,
        null=True,
        related_name="orders",
    )

    # Status
    status = models.CharField(
        max_length=20,
        choices=OrderStatus.choices,
        default=OrderStatus.PENDING,
    )

    # Pricing
    subtotal = models.DecimalField(max_digits=10, decimal_places=2)
    tax = models.DecimalField(max_digits=10, decimal_places=2, default=0)
    shipping_cost = models.DecimalField(max_digits=10, decimal_places=2, default=0)
    discount = models.DecimalField(max_digits=10, decimal_places=2, default=0)
    total = models.DecimalField(max_digits=10, decimal_places=2)
    currency = models.CharField(max_length=3, default="USD")

    # Addresses (JSON for flexibility with international addresses)
    shipping_address = models.JSONField()
    billing_address = models.JSONField()

    # Payment
    payment_method = models.CharField(max_length=50)  # stripe, paypal, cod
    payment_status = models.CharField(max_length=50, default="pending")
    payment_id = models.CharField(max_length=255, blank=True)
    payment_data = models.JSONField(blank=True, null=True)

    # Shipping
    shipping_method = models.CharField(max_length=100, blank=True)
    tracking_number = models.CharField(max_length=255, blank=True)
    carrier = models.CharField(max_length=100, blank=True)
    estimated_delivery = models.DateTimeField(blank=True, null=True)
    actual_delivery = models.DateTimeField(blank=True, null=True)

    # Notes
    customer_notes = models.TextField(blank=True)
    admin_notes = models.TextField(blank=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    confirmed_at = models.DateTimeField(blank=True, null=True)
    shipped_at = models.DateTimeField(blank=True, null=True)
    delivered_at = models.DateTimeField(blank=True, null=True)
    cancelled_at = models.DateTimeField(blank=True, null=True)

    def __str__(self) -> str:
        return self.order_number

    def save(self, *args, **kwargs):
        """Override save to generate order number."""
        if not self.order_number:
            self.order_number = self.generate_order_number()
        super().save(*args, **kwargs)

    def generate_order_number(self) -> str:
        """Generate a unique order number."""
        import uuid

        return f"ORD-{uuid.uuid4().hex[:12].upper()}"

    @property
    def items_count(self) -> int:
        """Get the total number of items in the order."""
        return sum(item.quantity for item in self.items.all())

    def confirm(self) -> None:
        """Confirm the order."""
        self.status = OrderStatus.CONFIRMED
        self.confirmed_at = timezone.now()
        self.save()

        # Trigger confirmation tasks
        from backend.tasks.order_tasks import process_order_confirmation

        process_order_confirmation.delay(self.id)

    def ship(self, tracking_number: str, carrier: str) -> None:
        """Mark order as shipped."""
        self.status = OrderStatus.SHIPPED
        self.tracking_number = tracking_number
        self.carrier = carrier
        self.shipped_at = timezone.now()
        self.save()

        # Send shipping notification
        from backend.tasks.email_tasks import send_shipping_confirmation

        send_shipping_confirmation.delay(self.id)

    def deliver(self) -> None:
        """Mark order as delivered."""
        self.status = OrderStatus.DELIVERED
        self.actual_delivery = timezone.now()
        self.delivered_at = timezone.now()
        self.save()

    def cancel(self) -> None:
        """Cancel the order."""
        if self.status not in [OrderStatus.SHIPPED, OrderStatus.DELIVERED]:
            self.status = OrderStatus.CANCELLED
            self.cancelled_at = timezone.now()
            self.save()

            # Process refund if applicable
            if self.payment_status == "paid":
                from backend.tasks.payment_tasks import process_refund

                process_refund.delay(self.id)

    def refund(self) -> None:
        """Process a refund for the order."""
        self.status = OrderStatus.REFUNDED
        self.payment_status = "refunded"
        self.save()


class OrderItem(models.Model):
    """
    Individual item in an order.
    """

    class Meta:
        db_table = "order_items"
        verbose_name = "Order Item"
        verbose_name_plural = "Order Items"
        ordering = ["id"]

    order = models.ForeignKey(
        Order,
        on_delete=models.CASCADE,
        related_name="items",
    )
    product = models.ForeignKey(
        "Product",
        on_delete=models.SET_NULL,
        null=True,
    )

    # Snapshot of product data at time of order
    product_name = models.CharField(max_length=255)
    product_slug = models.CharField(max_length=255)
    product_image = models.URLField(max_length=500)
    unit_price = models.DecimalField(max_digits=10, decimal_places=2)
    quantity = models.PositiveIntegerField(default=1)
    subtotal = models.DecimalField(max_digits=10, decimal_places=2)

    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"{self.product_name} x {self.quantity}"

    def save(self, *args, **kwargs):
        """Override save to calculate subtotal."""
        self.subtotal = self.unit_price * self.quantity
        super().save(*args, **kwargs)


# TODO: Add OrderTracking model for detailed tracking history
# TODO: Add OrderReturn model
# TODO: Add Invoice model
