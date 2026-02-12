"""
Order Service

Business logic for order operations.
"""

from typing import Optional
from decimal import Decimal
from django.db import transaction
from django.utils import timezone

from backend.core.models import (
    Order,
    OrderItem,
    OrderStatus,
    Cart,
    CartItem,
    Product,
)
from backend.api.services.cart_service import CartService


class OrderService:
    """Service class for order-related business logic."""

    def __init__(self):
        self.cart_service = CartService()

    def create_order(
        self,
        user,
        cart_data: dict,
    ) -> Order:
        """
        Create a new order from cart data.

        Args:
            user: The user placing the order
            cart_data: Dictionary containing cart items, addresses, etc.

        Returns:
            Created Order object

        Raises:
            ValueError: If cart is empty or products are out of stock
        """
        cart = self.cart_service.get_user_cart(user.id)
        cart_items = cart.items.select_related("product").all()

        if not cart_items:
            raise ValueError("Cannot create order with empty cart")

        # Validate stock
        for item in cart_items:
            if item.quantity > item.product.stock:
                raise ValueError(
                    f"Insufficient stock for {item.product.name}. "
                    f"Available: {item.product.stock}, Requested: {item.quantity}"
                )

        with transaction.atomic():
            # Calculate totals
            subtotal = Decimal(sum(item.subtotal for item in cart_items))
            tax = self._calculate_tax(subtotal, cart_data.get("shipping_address", {}))
            shipping_cost = self._calculate_shipping_cost(
                cart_data.get("shipping_address", {}),
                [item.product for item in cart_items],
            )
            discount = Decimal("0.00")
            total = subtotal + tax + shipping_cost - discount

            # Create order
            order = Order.objects.create(
                user=user,
                status=OrderStatus.PENDING,
                subtotal=subtotal,
                tax=tax,
                shipping_cost=shipping_cost,
                discount=discount,
                total=total,
                shipping_address=cart_data.get("shipping_address", {}),
                billing_address=cart_data.get(
                    "billing_address",
                    cart_data.get("shipping_address", {}),
                ),
                payment_method=cart_data.get("payment_method", "stripe"),
                customer_notes=cart_data.get("notes", ""),
            )

            # Create order items
            for item in cart_items:
                OrderItem.objects.create(
                    order=order,
                    product=item.product,
                    product_name=item.product.name,
                    product_slug=item.product.slug,
                    product_image=item.product.main_image.url if item.product.main_image else "",
                    unit_price=item.unit_price,
                    quantity=item.quantity,
                    subtotal=item.subtotal,
                )

                # Decrease stock
                item.product.stock -= item.quantity
                item.product.save(update_fields=["stock"])

            # Clear cart
            cart.clear()

        return order

    def confirm_order(self, order: Order) -> None:
        """
        Confirm an order after successful payment.

        Args:
            order: The order to confirm
        """
        order.confirm()

    def cancel_order(self, order: Order, user_id: int) -> bool:
        """
        Cancel an order if eligible.

        Args:
            order: The order to cancel
            user_id: The ID of the user requesting cancellation

        Returns:
            True if cancelled, False otherwise
        """
        if order.user_id != user_id:
            return False

        if order.status in [OrderStatus.SHIPPED, OrderStatus.DELIVERED]:
            return False

        with transaction.atomic():
            order.cancel()

            # Restore stock
            for item in order.items.all():
                if item.product:
                    item.product.stock += item.quantity
                    item.product.save(update_fields=["stock"])

        return True

    def refund_order(self, order: Order) -> bool:
        """
        Process refund for an order.

        Args:
            order: The order to refund

        Returns:
            True if refund processed, False otherwise
        """
        if order.payment_status != "paid":
            return False

        with transaction.atomic():
            # TODO: Process refund via payment gateway
            order.refund()

        return True

    def _calculate_tax(self, subtotal: Decimal, address: dict) -> Decimal:
        """
        Calculate tax based on shipping address.

        Args:
            subtotal: Order subtotal
            address: Shipping address

        Returns:
            Tax amount
        """
        # TODO: Implement proper tax calculation based on location
        # For now, using a flat 8% tax rate
        tax_rate = Decimal("0.08")
        return (subtotal * tax_rate).quantize(Decimal("0.01"))

    def _calculate_shipping_cost(self, address: dict, products: list) -> Decimal:
        """
        Calculate shipping cost based on address and products.

        Args:
            address: Shipping address
            products: List of products

        Returns:
            Shipping cost
        """
        # TODO: Implement proper shipping calculation
        # For now, using a flat $9.99 rate with free shipping over $50
        return Decimal("9.99")

    def update_order_status(
        self,
        order_id: str,
        status: OrderStatus,
    ) -> Optional[Order]:
        """
        Update order status.

        Args:
            order_id: The order ID
            status: New status

        Returns:
            Updated Order or None
        """
        try:
            order = Order.objects.get(id=order_id)
            order.status = status
            order.save()
            return order
        except Order.DoesNotExist:
            return None

    def get_user_orders(
        self,
        user_id: int,
        status: Optional[OrderStatus] = None,
        page: int = 1,
        page_size: int = 20,
    ) -> tuple[list[Order], int]:
        """
        Get user's orders with optional filtering.

        Args:
            user_id: The user ID
            status: Filter by status
            page: Page number
            page_size: Items per page

        Returns:
            Tuple of (orders list, total count)
        """
        queryset = Order.objects.filter(user_id=user_id)

        if status:
            queryset = queryset.filter(status=status)

        queryset = queryset.order_by("-created_at")

        total = queryset.count()
        start = (page - 1) * page_size
        end = start + page_size
        orders = list(queryset[start:end])

        return orders, total


# TODO: Add order export functionality
# TODO: Add order analytics/reporting
