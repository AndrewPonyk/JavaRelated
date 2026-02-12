"""
Inventory Models

Models for inventory tracking and management.
"""

from django.db import models
from django.core.cache import cache


class InventoryItem(models.Model):
    """
    Inventory item for tracking product stock across warehouses.
    """

    class Meta:
        db_table = "inventory_items"
        verbose_name = "Inventory Item"
        verbose_name_plural = "Inventory Items"
        unique_together = [["product", "warehouse"]]
        indexes = [
            models.Index(fields=["product"]),
            models.Index(fields=["warehouse"]),
            models.Index(fields=["quantity"]),
        ]

    product = models.ForeignKey(
        "Product",
        on_delete=models.CASCADE,
        related_name="inventory_items",
    )
    warehouse = models.ForeignKey(
        "Warehouse",
        on_delete=models.CASCADE,
        related_name="inventory_items",
    )
    quantity = models.IntegerField(default=0)
    reserved_quantity = models.IntegerField(default=0)
    reorder_level = models.IntegerField(default=10)
    reorder_quantity = models.IntegerField(default=50)
    location = models.CharField(max_length=100, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"{self.product.name} @ {self.warehouse.name}"

    @property
    def available_quantity(self) -> int:
        """Get quantity available for new orders."""
        return max(0, self.quantity - self.reserved_quantity)

    @property
    def is_low_stock(self) -> bool:
        """Check if item is below reorder level."""
        return self.available_quantity <= self.reorder_level

    def needs_reorder(self) -> bool:
        """Check if item needs to be reordered."""
        return self.available_quantity <= self.reorder_level


class InventoryTransaction(models.Model):
    """
    Transaction history for inventory changes.
    """

    class Meta:
        db_table = "inventory_transactions"
        verbose_name = "Inventory Transaction"
        verbose_name_plural = "Inventory Transactions"
        indexes = [
            models.Index(fields=["product"]),
            models.Index(fields=["transaction_type"]),
            models.Index(fields=["-created_at"]),
            models.Index(fields=["reference_id", "reference_type"]),
        ]

    TRANSACTION_TYPES = [
        ("purchase", "Purchase"),
        ("sale", "Sale"),
        ("adjustment", "Adjustment"),
        ("transfer", "Transfer"),
        ("return", "Return"),
        ("damage", "Damage"),
        ("loss", "Loss"),
    ]

    product = models.ForeignKey(
        "Product",
        on_delete=models.CASCADE,
        related_name="inventory_transactions",
    )
    warehouse = models.ForeignKey(
        "Warehouse",
        on_delete=models.CASCADE,
        related_name="inventory_transactions",
    )
    transaction_type = models.CharField(
        max_length=20,
        choices=TRANSACTION_TYPES,
    )
    quantity = models.IntegerField(help_text="Positive for additions, negative for subtractions")
    quantity_before = models.IntegerField()
    quantity_after = models.IntegerField()

    # Reference to related entity (order, transfer, etc.)
    reference_type = models.CharField(max_length=50, blank=True)
    reference_id = models.CharField(max_length=100, blank=True)

    metadata = models.JSONField(default=dict, blank=True)

    # Tracking
    performed_by = models.ForeignKey(
        "User",
        on_delete=models.SET_NULL,
        null=True,
        related_name="performed_inventory_transactions",
    )
    notes = models.TextField(blank=True)

    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"{self.transaction_type}: {self.product.name} ({self.quantity})"


class Warehouse(models.Model):
    """
    Warehouse or storage location.
    """

    class Meta:
        db_table = "warehouses"
        verbose_name = "Warehouse"
        verbose_name_plural = "Warehouses"
        ordering = ["name"]

    name = models.CharField(max_length=200)
    code = models.SlugField(max_length=50, unique=True)
    address = models.JSONField(default=dict)
    is_active = models.BooleanField(default=True)

    # Capacity tracking
    capacity = models.IntegerField(
        null=True,
        blank=True,
        help_text="Maximum capacity in units",
    )

    # Contact
    manager_name = models.CharField(max_length=200, blank=True)
    manager_email = models.EmailField(blank=True)
    manager_phone = models.CharField(max_length=20, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return self.name

    @property
    def current_capacity(self) -> int:
        """Get current inventory count."""
        return self.inventory_items.aggregate(
            total=models.Sum("quantity")
        )["total"] or 0

    @property
    def capacity_utilization(self) -> float:
        """Get capacity utilization percentage."""
        if not self.capacity or self.capacity == 0:
            return 0.0
        return (self.current_capacity / self.capacity) * 100


class StockAlert(models.Model):
    """
    Stock alerts for low inventory or out of stock items.
    """

    class Meta:
        db_table = "stock_alerts"
        verbose_name = "Stock Alert"
        verbose_name_plural = "Stock Alerts"
        indexes = [
            models.Index(fields=["product"]),
            models.Index(fields=["alert_type"]),
            models.Index(fields=["is_resolved", "-created_at"]),
        ]

    ALERT_TYPES = [
        ("low_stock", "Low Stock"),
        ("out_of_stock", "Out of Stock"),
        ("overstock", "Overstock"),
        ("expired", "Expired"),
    ]

    product = models.ForeignKey(
        "Product",
        on_delete=models.CASCADE,
        related_name="stock_alerts",
    )
    warehouse = models.ForeignKey(
        "Warehouse",
        on_delete=models.CASCADE,
        related_name="stock_alerts",
        null=True,
        blank=True,
    )
    alert_type = models.CharField(
        max_length=20,
        choices=ALERT_TYPES,
    )
    threshold = models.IntegerField()
    current_quantity = models.IntegerField()

    message = models.TextField()
    is_resolved = models.BooleanField(default=False)

    # Resolution tracking
    resolved_at = models.DateTimeField(blank=True, null=True)
    resolved_by = models.ForeignKey(
        "User",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="resolved_stock_alerts",
    )
    resolution_notes = models.TextField(blank=True)

    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self) -> str:
        return f"{self.alert_type}: {self.product.name}"

    def resolve(self, user_id: int, notes: str = "") -> None:
        """Mark alert as resolved."""
        from django.utils import timezone

        self.is_resolved = True
        self.resolved_at = timezone.now()
        self.resolved_by_id = user_id
        self.resolution_notes = notes
        self.save(update_fields=["is_resolved", "resolved_at", "resolved_by_id", "resolution_notes"])


class StockMovement(models.Model):
    """
    Stock movement between warehouses.
    """

    class Meta:
        db_table = "stock_movements"
        verbose_name = "Stock Movement"
        verbose_name_plural = "Stock Movements"
        indexes = [
            models.Index(fields=["product"]),
            models.Index(fields=["status"]),
            models.Index(fields=["-created_at"]),
        ]

    STATUS_CHOICES = [
        ("pending", "Pending"),
        ("in_transit", "In Transit"),
        ("completed", "Completed"),
        ("cancelled", "Cancelled"),
    ]

    product = models.ForeignKey(
        "Product",
        on_delete=models.CASCADE,
        related_name="stock_movements",
    )
    quantity = models.IntegerField()

    from_warehouse = models.ForeignKey(
        "Warehouse",
        on_delete=models.CASCADE,
        related_name="stock_movements_from",
    )
    to_warehouse = models.ForeignKey(
        "Warehouse",
        on_delete=models.CASCADE,
        related_name="stock_movements_to",
    )

    status = models.CharField(
        max_length=20,
        choices=STATUS_CHOICES,
        default="pending",
    )

    # Tracking
    tracking_number = models.CharField(max_length=100, blank=True)
    shipped_at = models.DateTimeField(blank=True, null=True)
    received_at = models.DateTimeField(blank=True, null=True)

    # Requested by
    requested_by = models.ForeignKey(
        "User",
        on_delete=models.SET_NULL,
        null=True,
        related_name="requested_stock_movements",
    )

    notes = models.TextField(blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"{self.product.name}: {self.from_warehouse.name} -> {self.to_warehouse.name}"

    def ship(self, tracking_number: str = "") -> None:
        """Mark movement as shipped."""
        from django.utils import timezone

        self.status = "in_transit"
        self.shipped_at = timezone.now()
        if tracking_number:
            self.tracking_number = tracking_number
        self.save(update_fields=["status", "shipped_at", "tracking_number"])

    def receive(self) -> None:
        """Mark movement as received and update inventory."""
        from django.utils import timezone
        from django.db import transaction

        with transaction.atomic():
            # Update inventory
            # Decrease from source warehouse
            source_item = InventoryItem.objects.filter(
                product=self.product,
                warehouse=self.from_warehouse,
            ).first()

            if source_item:
                source_item.quantity -= self.quantity
                source_item.save(update_fields=["quantity"])

            # Increase in destination warehouse
            dest_item, _ = InventoryItem.objects.get_or_create(
                product=self.product,
                warehouse=self.to_warehouse,
                defaults={"quantity": self.quantity},
            )
            dest_item.quantity += self.quantity
            dest_item.save(update_fields=["quantity"])

            # Create transactions
            InventoryTransaction.objects.create(
                product=self.product,
                warehouse=self.from_warehouse,
                transaction_type="transfer",
                quantity=-self.quantity,
                quantity_before=source_item.quantity + self.quantity if source_item else 0,
                quantity_after=source_item.quantity if source_item else 0,
                reference_type="stock_movement",
                reference_id=str(self.id),
            )

            InventoryTransaction.objects.create(
                product=self.product,
                warehouse=self.to_warehouse,
                transaction_type="transfer",
                quantity=self.quantity,
                quantity_before=dest_item.quantity - self.quantity,
                quantity_after=dest_item.quantity,
                reference_type="stock_movement",
                reference_id=str(self.id),
            )

            # Mark as received
            self.status = "completed"
            self.received_at = timezone.now()
            self.save(update_fields=["status", "received_at"])


# Import at end to avoid circular imports
from backend.core.models.user import User
from backend.core.models.product import Product
