"""Inventory models for E-Commerce Platform."""
from django.db import models


class InventoryItem(models.Model):
    """Inventory tracking for products and variants."""

    product = models.ForeignKey(
        'products.Product',
        on_delete=models.CASCADE,
        related_name='inventory'
    )
    variant = models.ForeignKey(
        'products.ProductVariant',
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        related_name='inventory'
    )

    # Stock levels
    quantity_on_hand = models.PositiveIntegerField(default=0)
    quantity_reserved = models.PositiveIntegerField(default=0)
    quantity_available = models.PositiveIntegerField(default=0)

    # Thresholds
    reorder_point = models.PositiveIntegerField(default=10)
    reorder_quantity = models.PositiveIntegerField(default=50)

    # Tracking
    last_restocked_at = models.DateTimeField(null=True, blank=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'inventory item'
        verbose_name_plural = 'inventory items'
        unique_together = ['product', 'variant']

    def __str__(self):
        if self.variant:
            return f"{self.product.name} - {self.variant.name}: {self.quantity_available}"
        return f"{self.product.name}: {self.quantity_available}"

    def save(self, *args, **kwargs):
        """Calculate available quantity."""
        self.quantity_available = max(0, self.quantity_on_hand - self.quantity_reserved)
        super().save(*args, **kwargs)

    @property
    def is_low_stock(self) -> bool:
        """Check if stock is at or below reorder point."""
        return self.quantity_available <= self.reorder_point

    @property
    def is_out_of_stock(self) -> bool:
        """Check if item is out of stock."""
        return self.quantity_available == 0


class InventoryTransaction(models.Model):
    """Track all inventory movements."""

    class TransactionType(models.TextChoices):
        RECEIVED = 'received', 'Received'
        SOLD = 'sold', 'Sold'
        RETURNED = 'returned', 'Returned'
        ADJUSTED = 'adjusted', 'Adjusted'
        RESERVED = 'reserved', 'Reserved'
        RELEASED = 'released', 'Released'

    inventory_item = models.ForeignKey(
        InventoryItem,
        on_delete=models.CASCADE,
        related_name='transactions'
    )
    transaction_type = models.CharField(
        max_length=20,
        choices=TransactionType.choices
    )
    quantity = models.IntegerField()  # Positive for additions, negative for removals
    reference_type = models.CharField(max_length=50, blank=True)  # e.g., 'order', 'adjustment'
    reference_id = models.CharField(max_length=100, blank=True)
    notes = models.TextField(blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    created_by = models.ForeignKey(
        'users.User',
        on_delete=models.SET_NULL,
        null=True,
        blank=True
    )

    class Meta:
        verbose_name = 'inventory transaction'
        verbose_name_plural = 'inventory transactions'
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.transaction_type}: {self.quantity} - {self.inventory_item}"


class StockAlert(models.Model):
    """Low stock alerts for inventory monitoring."""

    class AlertStatus(models.TextChoices):
        ACTIVE = 'active', 'Active'
        ACKNOWLEDGED = 'acknowledged', 'Acknowledged'
        RESOLVED = 'resolved', 'Resolved'

    inventory_item = models.ForeignKey(
        InventoryItem,
        on_delete=models.CASCADE,
        related_name='alerts'
    )
    status = models.CharField(
        max_length=20,
        choices=AlertStatus.choices,
        default=AlertStatus.ACTIVE
    )
    threshold_triggered = models.PositiveIntegerField()
    current_quantity = models.PositiveIntegerField()

    created_at = models.DateTimeField(auto_now_add=True)
    acknowledged_at = models.DateTimeField(null=True, blank=True)
    resolved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = 'stock alert'
        verbose_name_plural = 'stock alerts'
        ordering = ['-created_at']
