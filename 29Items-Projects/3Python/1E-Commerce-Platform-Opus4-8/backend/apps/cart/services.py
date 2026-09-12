"""Cart service layer — line-item management with inventory validation.

Per PROJECT-PLAN §2, the cart validates requested quantities against available
inventory at add/update time. Authoritative stock *reservation* happens later,
atomically, at checkout (see apps.orders.services).
"""

from __future__ import annotations

from django.db import transaction

from apps.catalog.models import Product
from common.utils.exceptions import DomainError

from .models import Cart, CartItem


class ProductNotPurchasableError(DomainError):
    status_code = 422
    code = "PRODUCT_NOT_PURCHASABLE"


class InsufficientStockForCartError(DomainError):
    status_code = 409
    code = "INVENTORY_INSUFFICIENT"


def get_or_create_active_cart(*, user) -> Cart:
    cart, _ = Cart.objects.get_or_create(user=user, is_active=True)
    return cart


def _available(product: Product) -> int:
    stock = getattr(product, "stock", None)
    return stock.quantity_available if stock else 0


@transaction.atomic
def add_item(*, cart: Cart, product_id: int, quantity: int) -> CartItem:
    if quantity < 1:
        raise DomainError("Quantity must be at least 1.")

    try:
        product = Product.objects.select_related("stock").get(id=product_id)
    except Product.DoesNotExist as exc:
        raise DomainError("Product does not exist.") from exc

    if not product.is_purchasable:
        raise ProductNotPurchasableError("This product is not available for purchase.")

    existing = cart.items.filter(product=product).first()
    desired = quantity + (existing.quantity if existing else 0)

    if _available(product) < desired:
        raise InsufficientStockForCartError(
            "Not enough stock available.",
            details={
                "product_id": product_id,
                "requested": desired,
                "available": _available(product),
            },
        )

    if existing:
        existing.quantity = desired
        existing.save(update_fields=["quantity"])
        return existing

    return CartItem.objects.create(
        cart=cart, product=product, quantity=quantity, unit_price=product.price
    )


@transaction.atomic
def update_item(*, item: CartItem, quantity: int) -> CartItem:
    if quantity < 1:
        raise DomainError("Quantity must be at least 1.")
    product = Product.objects.select_related("stock").get(id=item.product_id)
    if _available(product) < quantity:
        raise InsufficientStockForCartError(
            "Not enough stock available.",
            details={
                "product_id": product.id,
                "requested": quantity,
                "available": _available(product),
            },
        )
    item.quantity = quantity
    item.save(update_fields=["quantity"])
    return item


def remove_item(*, item: CartItem) -> None:
    item.delete()


def clear(*, cart: Cart) -> None:
    cart.items.all().delete()
