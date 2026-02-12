"""
Inventory Service

Business logic for inventory management.
"""

from typing import List, Dict, Any, Optional
from decimal import Decimal
from datetime import datetime, timedelta
from django.db import transaction
from django.db.models import Sum, F, Q
from django.core.cache import cache
from django.utils import timezone
import logging

logger = logging.getLogger("backend")

from backend.core.models import Product


class InventoryService:
    """Service class for inventory-related business logic."""

    def __init__(self):
        self.cache_prefix = "inventory"
        self.cache_ttl = 300  # 5 minutes

    def get_stock_level(self, product_id: int) -> Dict[str, Any]:
        """
        Get current stock level for a product.

        Args:
            product_id: The product ID

        Returns:
            Dictionary with stock information
        """
        cache_key = f"{self.cache_prefix}:stock:{product_id}"
        stock_data = cache.get(cache_key)

        if stock_data is None:
            try:
                product = Product.objects.get(id=product_id)
                stock_data = {
                    "product_id": product.id,
                    "product_name": product.name,
                    "current_stock": product.stock,
                    "status": self._get_stock_status(product.stock),
                    "low_stock_threshold": 10,
                }
                cache.set(cache_key, stock_data, self.cache_ttl)
            except Product.DoesNotExist:
                return {
                    "product_id": product_id,
                    "current_stock": 0,
                    "status": "out_of_stock",
                    "error": "Product not found",
                }

        return stock_data

    def reserve_stock(
        self,
        product_id: int,
        quantity: int,
        reference_id: Optional[str] = None,
        reference_type: str = "order",
    ) -> bool:
        """
        Reserve inventory for an order.

        Args:
            product_id: The product ID
            quantity: Quantity to reserve
            reference_id: Optional reference ID (e.g., order_id)
            reference_type: Type of reference (order, transfer, etc.)

        Returns:
            True if stock reserved successfully

        Raises:
            ValueError: If insufficient stock
        """
        with transaction.atomic():
            try:
                product = Product.objects.select_for_update().get(id=product_id)

                if product.stock < quantity:
                    raise ValueError(
                        f"Insufficient stock. Available: {product.stock}, Requested: {quantity}"
                    )

                # Decrease stock
                product.stock -= quantity
                product.save(update_fields=["stock"])

                # Create inventory transaction record
                self._create_transaction(
                    product=product,
                    quantity=-quantity,
                    transaction_type="reservation",
                    reference_id=reference_id,
                    reference_type=reference_type,
                )

                # Invalidate cache
                cache.delete(f"{self.cache_prefix}:stock:{product_id}")

                logger.info(
                    f"Reserved {quantity} units of product {product_id} "
                    f"for {reference_type}:{reference_id}"
                )

                return True

            except Product.DoesNotExist:
                raise ValueError("Product not found")

    def release_stock(
        self,
        product_id: int,
        quantity: int,
        reference_id: Optional[str] = None,
        reference_type: str = "order_cancellation",
    ) -> bool:
        """
        Release reserved inventory back to stock.

        Args:
            product_id: The product ID
            quantity: Quantity to release
            reference_id: Optional reference ID
            reference_type: Type of reference

        Returns:
            True if stock released successfully
        """
        with transaction.atomic():
            try:
                product = Product.objects.select_for_update().get(id=product_id)

                # Increase stock
                product.stock += quantity
                product.save(update_fields=["stock"])

                # Create inventory transaction record
                self._create_transaction(
                    product=product,
                    quantity=quantity,
                    transaction_type="release",
                    reference_id=reference_id,
                    reference_type=reference_type,
                )

                # Invalidate cache
                cache.delete(f"{self.cache_prefix}:stock:{product_id}")

                logger.info(
                    f"Released {quantity} units of product {product_id} "
                    f"from {reference_type}:{reference_id}"
                )

                return True

            except Product.DoesNotExist:
                logger.error(f"Product {product_id} not found for stock release")
                return False

    def adjust_stock(
        self,
        product_id: int,
        quantity: int,
        reason: str,
        reference_id: Optional[str] = None,
    ) -> bool:
        """
        Manually adjust stock level (addition or subtraction).

        Args:
            product_id: The product ID
            quantity: Quantity to adjust (positive or negative)
            reason: Reason for adjustment
            reference_id: Optional reference ID

        Returns:
            True if adjustment successful
        """
        with transaction.atomic():
            try:
                product = Product.objects.select_for_update().get(id=product_id)

                # Ensure stock doesn't go negative
                new_stock = product.stock + quantity
                if new_stock < 0:
                    raise ValueError(
                        f"Stock cannot be negative. Current: {product.stock}, Adjustment: {quantity}"
                    )

                product.stock = new_stock
                product.save(update_fields=["stock"])

                # Create inventory transaction record
                self._create_transaction(
                    product=product,
                    quantity=quantity,
                    transaction_type="adjustment",
                    reference_id=reference_id,
                    metadata={"reason": reason},
                )

                # Invalidate cache
                cache.delete(f"{self.cache_prefix}:stock:{product_id}")

                logger.info(
                    f"Adjusted stock for product {product_id} by {quantity}. "
                    f"Reason: {reason}"
                )

                return True

            except Product.DoesNotExist:
                logger.error(f"Product {product_id} not found for stock adjustment")
                return False

    def bulk_adjust_stock(
        self,
        adjustments: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        """
        Adjust stock for multiple products in a single transaction.

        Args:
            adjustments: List of dictionaries with product_id and quantity

        Returns:
            Dictionary with adjustment results
        """
        with transaction.atomic():
            success_count = 0
            errors = []

            for adj in adjustments:
                product_id = adj.get("product_id")
                quantity = adj.get("quantity", 0)
                reason = adj.get("reason", "Bulk adjustment")

                try:
                    if self.adjust_stock(product_id, quantity, reason):
                        success_count += 1
                    else:
                        errors.append({
                            "product_id": product_id,
                            "error": "Adjustment failed",
                        })
                except Exception as e:
                    errors.append({
                        "product_id": product_id,
                        "error": str(e),
                    })

            return {
                "total": len(adjustments),
                "success": success_count,
                "errors": errors,
            }

    def get_low_stock_products(
        self,
        threshold: int = 10,
        vendor_id: Optional[int] = None,
    ) -> List[Dict[str, Any]]:
        """
        Get products with stock below threshold.

        Args:
            threshold: Low stock threshold
            vendor_id: Optional vendor ID to filter by

        Returns:
            List of products with low stock
        """
        queryset = Product.objects.filter(
            is_active=True,
            stock__lte=threshold,
        )

        if vendor_id:
            queryset = queryset.filter(vendor_id=vendor_id)

        products = [
            {
                "product_id": p.id,
                "product_name": p.name,
                "current_stock": p.stock,
                "threshold": threshold,
                "status": self._get_stock_status(p.stock),
                "vendor_id": p.vendor_id,
            }
            for p in queryset
        ]

        return products

    def get_stock_alerts(self) -> List[Dict[str, Any]]:
        """
        Get all stock alerts including low stock and out of stock.

        Returns:
            List of stock alerts
        """
        # Out of stock products
        out_of_stock = Product.objects.filter(
            is_active=True,
            stock=0,
        ).values("id", "name", "vendor_id")

        # Low stock products
        low_stock = Product.objects.filter(
            is_active=True,
            stock__gt=0,
            stock__lte=10,
        ).values("id", "name", "stock", "vendor_id")

        alerts = []

        for item in out_of_stock:
            alerts.append({
                "product_id": item["id"],
                "product_name": item["name"],
                "vendor_id": item["vendor_id"],
                "alert_type": "out_of_stock",
                "severity": "critical",
                "current_stock": 0,
            })

        for item in low_stock:
            alerts.append({
                "product_id": item["id"],
                "product_name": item["name"],
                "vendor_id": item["vendor_id"],
                "alert_type": "low_stock",
                "severity": "warning",
                "current_stock": item["stock"],
            })

        return sorted(alerts, key=lambda x: (x["severity"], x["current_stock"]))

    def get_stock_transactions(
        self,
        product_id: Optional[int] = None,
        transaction_type: Optional[str] = None,
        limit: int = 100,
    ) -> List[Dict[str, Any]]:
        """
        Get inventory transaction history.

        Args:
            product_id: Optional product ID filter
            transaction_type: Optional transaction type filter
            limit: Maximum number of transactions

        Returns:
            List of inventory transactions
        """
        # TODO: Implement after creating InventoryTransaction model
        return []

    def forecast_demand(
        self,
        product_id: int,
        days: int = 30,
    ) -> Dict[str, Any]:
        """
        Forecast demand for a product based on historical data.

        Args:
            product_id: The product ID
            days: Number of days to forecast

        Returns:
            Dictionary with demand forecast
        """
        # TODO: Implement demand forecasting using historical order data
        # For now, return simple average daily usage
        return {
            "product_id": product_id,
            "forecast_days": days,
            "predicted_demand": 0,
            "reorder_suggestion": False,
        }

    def _get_stock_status(self, stock: int) -> str:
        """Get stock status label."""
        if stock == 0:
            return "out_of_stock"
        elif stock <= 5:
            return "critical"
        elif stock <= 10:
            return "low"
        elif stock <= 20:
            return "moderate"
        else:
            return "good"

    def _create_transaction(
        self,
        product: Product,
        quantity: int,
        transaction_type: str,
        reference_id: Optional[str] = None,
        reference_type: str = "manual",
        metadata: Optional[Dict[str, Any]] = None,
    ) -> None:
        """
        Create an inventory transaction record.

        Args:
            product: The product instance
            quantity: Quantity changed (positive or negative)
            transaction_type: Type of transaction
            reference_id: Optional reference ID
            reference_type: Type of reference
            metadata: Optional additional metadata
        """
        # TODO: Implement after creating InventoryTransaction model
        logger.info(
            f"Inventory transaction: product={product.id}, "
            f"type={transaction_type}, qty={quantity}, "
            f"ref={reference_type}:{reference_id}"
        )
