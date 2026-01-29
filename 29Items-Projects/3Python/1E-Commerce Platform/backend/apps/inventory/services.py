"""Inventory service layer for E-Commerce Platform."""
from typing import Optional

from django.db import transaction
from django.utils import timezone

from apps.products.models import Product, ProductVariant

from .models import InventoryItem, InventoryTransaction, StockAlert


class InventoryService:
    """Service class for inventory-related business logic."""

    @staticmethod
    def get_available_stock(
        product: Product,
        variant: Optional[ProductVariant] = None
    ) -> int:
        """Get available stock for a product/variant."""
        try:
            item = InventoryItem.objects.get(product=product, variant=variant)
            return item.quantity_available
        except InventoryItem.DoesNotExist:
            return 0

    @staticmethod
    @transaction.atomic
    def reserve_stock(
        product: Product,
        variant: Optional[ProductVariant],
        quantity: int,
        reference_type: str = '',
        reference_id: str = ''
    ) -> bool:
        """Reserve stock for an order."""
        item = InventoryItem.objects.select_for_update().get(
            product=product,
            variant=variant
        )

        if item.quantity_available < quantity:
            return False

        item.quantity_reserved += quantity
        item.save()

        InventoryTransaction.objects.create(
            inventory_item=item,
            transaction_type=InventoryTransaction.TransactionType.RESERVED,
            quantity=-quantity,
            reference_type=reference_type,
            reference_id=reference_id
        )

        return True

    @staticmethod
    @transaction.atomic
    def release_reservation(
        product: Product,
        variant: Optional[ProductVariant],
        quantity: int,
        reference_type: str = '',
        reference_id: str = ''
    ) -> None:
        """Release previously reserved stock."""
        item = InventoryItem.objects.select_for_update().get(
            product=product,
            variant=variant
        )

        item.quantity_reserved = max(0, item.quantity_reserved - quantity)
        item.save()

        InventoryTransaction.objects.create(
            inventory_item=item,
            transaction_type=InventoryTransaction.TransactionType.RELEASED,
            quantity=quantity,
            reference_type=reference_type,
            reference_id=reference_id
        )

    @staticmethod
    @transaction.atomic
    def deduct_stock(
        product: Product,
        variant: Optional[ProductVariant],
        quantity: int,
        reference_type: str = 'order',
        reference_id: str = ''
    ) -> None:
        """Deduct stock when order is fulfilled."""
        item = InventoryItem.objects.select_for_update().get(
            product=product,
            variant=variant
        )

        item.quantity_on_hand -= quantity
        item.quantity_reserved = max(0, item.quantity_reserved - quantity)
        item.save()

        InventoryTransaction.objects.create(
            inventory_item=item,
            transaction_type=InventoryTransaction.TransactionType.SOLD,
            quantity=-quantity,
            reference_type=reference_type,
            reference_id=reference_id
        )

        # Check for low stock alert
        if item.is_low_stock:
            InventoryService._create_low_stock_alert(item)

    @staticmethod
    @transaction.atomic
    def add_stock(
        product: Product,
        variant: Optional[ProductVariant],
        quantity: int,
        notes: str = '',
        user=None
    ) -> InventoryItem:
        """Add stock (restock)."""
        item, created = InventoryItem.objects.get_or_create(
            product=product,
            variant=variant
        )

        item.quantity_on_hand += quantity
        item.last_restocked_at = timezone.now()
        item.save()

        InventoryTransaction.objects.create(
            inventory_item=item,
            transaction_type=InventoryTransaction.TransactionType.RECEIVED,
            quantity=quantity,
            notes=notes,
            created_by=user
        )

        # Resolve any active low stock alerts
        StockAlert.objects.filter(
            inventory_item=item,
            status=StockAlert.AlertStatus.ACTIVE
        ).update(
            status=StockAlert.AlertStatus.RESOLVED,
            resolved_at=timezone.now()
        )

        return item

    @staticmethod
    @transaction.atomic
    def adjust_stock(
        inventory_item_id: int,
        quantity: int,
        reason: str,
        user=None
    ) -> InventoryTransaction:
        """Make manual stock adjustment."""
        item = InventoryItem.objects.select_for_update().get(id=inventory_item_id)

        if item.quantity_on_hand + quantity < 0:
            raise ValueError("Adjustment would result in negative stock.")

        item.quantity_on_hand += quantity
        item.save()

        return InventoryTransaction.objects.create(
            inventory_item=item,
            transaction_type=InventoryTransaction.TransactionType.ADJUSTED,
            quantity=quantity,
            notes=reason,
            created_by=user
        )

    @staticmethod
    def _create_low_stock_alert(item: InventoryItem) -> None:
        """Create a low stock alert if one doesn't exist."""
        existing = StockAlert.objects.filter(
            inventory_item=item,
            status=StockAlert.AlertStatus.ACTIVE
        ).exists()

        if not existing:
            StockAlert.objects.create(
                inventory_item=item,
                threshold_triggered=item.reorder_point,
                current_quantity=item.quantity_available
            )

    @staticmethod
    def acknowledge_alert(alert: StockAlert) -> None:
        """Acknowledge a stock alert."""
        alert.status = StockAlert.AlertStatus.ACKNOWLEDGED
        alert.acknowledged_at = timezone.now()
        alert.save()

    @staticmethod
    def resolve_alert(alert: StockAlert) -> None:
        """Resolve a stock alert."""
        alert.status = StockAlert.AlertStatus.RESOLVED
        alert.resolved_at = timezone.now()
        alert.save()
