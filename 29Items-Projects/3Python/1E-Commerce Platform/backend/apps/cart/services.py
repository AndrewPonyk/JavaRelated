"""Cart service layer for E-Commerce Platform."""
from decimal import Decimal
from typing import Optional
from uuid import UUID

from django.conf import settings
from django.db import transaction

from apps.inventory.services import InventoryService
from apps.products.models import Product, ProductVariant

from .models import Cart, CartItem


# |su:20 Service Layer Pattern: business logic lives here, not in views or models
# Why: keeps views thin (just HTTP handling), models thin (just data structure)
# Services can be reused across views, management commands, Celery tasks
class CartService:
    """Service class for cart-related business logic."""

    @staticmethod
    def get_or_create_cart(request) -> Cart:
        """Get existing cart or create new one for user/session."""
        if request.user.is_authenticated:
            # |su:21 prefetch_related() solves N+1 query problem for reverse FKs
            # Without it: 1 query for cart + N queries for each item's product
            # With it: 1 query for cart + 1 query for ALL related items
            cart, created = Cart.objects.prefetch_related(
                'items__product__images',  # items → product → images (3 levels deep)
                'items__product__category',
                'items__variant'
            ).get_or_create(user=request.user)
        else:
            session_key = request.session.session_key
            if not session_key:
                request.session.create()
                session_key = request.session.session_key

            cart, created = Cart.objects.prefetch_related(
                'items__product__images',
                'items__product__category',
                'items__variant'
            ).get_or_create(session_key=session_key)

        return cart

    @staticmethod
    # |su:22 @transaction.atomic: ALL DB operations succeed or ALL rollback
    # Without it: crash mid-function could leave DB in inconsistent state
    # (e.g., quantity updated but price not updated)
    @transaction.atomic
    def add_item(
        cart: Cart,
        product_id: UUID,
        quantity: int = 1,
        variant_id: Optional[int] = None
    ) -> CartItem:
        """Add item to cart or update quantity if exists."""
        try:
            product = Product.objects.get(
                id=product_id,
                status=Product.Status.ACTIVE
            )
        except Product.DoesNotExist:
            raise ValueError("Product not found or unavailable.")

        variant = None
        if variant_id:
            try:
                variant = ProductVariant.objects.get(
                    id=variant_id,
                    product=product,
                    is_active=True
                )
            except ProductVariant.DoesNotExist:
                raise ValueError("Product variant not found or unavailable.")

        # Check stock availability
        available_stock = InventoryService.get_available_stock(product, variant)

        # Get existing cart item quantity
        existing_quantity = 0
        try:
            existing_item = CartItem.objects.get(
                cart=cart,
                product=product,
                variant=variant
            )
            existing_quantity = existing_item.quantity
        except CartItem.DoesNotExist:
            pass

        total_requested = existing_quantity + quantity
        if total_requested > available_stock:
            if available_stock == 0:
                raise ValueError("Product is out of stock.")
            raise ValueError(f"Only {available_stock} items available. You already have {existing_quantity} in cart.")

        # Get or create cart item
        cart_item, created = CartItem.objects.get_or_create(
            cart=cart,
            product=product,
            variant=variant,
            defaults={
                'quantity': quantity,
                'unit_price': variant.effective_price if variant else product.price
            }
        )

        if not created:
            cart_item.quantity += quantity
            cart_item.save()

        return cart_item

    @staticmethod
    @transaction.atomic
    def update_item_quantity(cart: Cart, item_id: int, quantity: int) -> CartItem:
        """Update quantity of cart item."""
        try:
            cart_item = CartItem.objects.get(id=item_id, cart=cart)
        except CartItem.DoesNotExist:
            raise ValueError("Cart item not found.")

        if quantity <= 0:
            raise ValueError("Quantity must be greater than 0.")

        # Check stock availability
        available_stock = InventoryService.get_available_stock(
            cart_item.product, cart_item.variant
        )
        if quantity > available_stock:
            if available_stock == 0:
                raise ValueError("Product is out of stock.")
            raise ValueError(f"Only {available_stock} items available.")

        cart_item.quantity = quantity
        cart_item.save()
        return cart_item

    @staticmethod
    @transaction.atomic
    def remove_item(cart: Cart, item_id: int) -> None:
        """Remove item from cart."""
        deleted_count, _ = CartItem.objects.filter(id=item_id, cart=cart).delete()
        if deleted_count == 0:
            raise ValueError("Cart item not found.")

    @staticmethod
    @transaction.atomic
    def clear_cart(cart: Cart) -> None:
        """Remove all items from cart."""
        cart.items.all().delete()

    @staticmethod
    @transaction.atomic
    def merge_guest_cart(user, session_key: str) -> Cart:
        # |su:23 Cart merge pattern: when guest logs in, merge their anonymous cart
        # Scenarios: (1) guest has items, user has none → move items
        # (2) both have same product → combine quantities (respect stock limit)
        # (3) guest cart empty → just return user cart
        """Merge guest cart into user cart after login."""
        try:
            # user__isnull=True ensures we only get anonymous carts
            guest_cart = Cart.objects.get(session_key=session_key, user__isnull=True)
        except Cart.DoesNotExist:
            return Cart.objects.get_or_create(user=user)[0]

        user_cart, created = Cart.objects.get_or_create(user=user)

        # Merge items from guest cart
        for guest_item in guest_cart.items.all():
            try:
                user_item = CartItem.objects.get(
                    cart=user_cart,
                    product=guest_item.product,
                    variant=guest_item.variant
                )
                # Check stock before merging
                available_stock = InventoryService.get_available_stock(
                    guest_item.product, guest_item.variant
                )
                new_quantity = min(
                    user_item.quantity + guest_item.quantity,
                    available_stock
                )
                user_item.quantity = new_quantity
                user_item.save()
            except CartItem.DoesNotExist:
                guest_item.cart = user_cart
                guest_item.save()

        # Delete guest cart
        guest_cart.delete()

        return user_cart

    @staticmethod
    def calculate_cart_totals(cart: Cart, shipping_address: dict = None) -> dict:
        """Calculate all cart totals including tax and shipping estimates."""
        subtotal = cart.subtotal

        # Calculate tax based on shipping state (US tax rates)
        tax_rate = CartService._get_tax_rate(shipping_address)
        tax_amount = subtotal * tax_rate

        # Calculate shipping based on subtotal and item count
        shipping_estimate = CartService._calculate_shipping(cart, shipping_address)

        return {
            'subtotal': subtotal,
            'tax_rate': tax_rate,
            'tax_amount': tax_amount.quantize(Decimal('0.01')),
            'shipping_estimate': shipping_estimate,
            'total': (subtotal + tax_amount + shipping_estimate).quantize(Decimal('0.01')),
            'item_count': cart.item_count
        }

    @staticmethod
    def _get_tax_rate(shipping_address: dict = None) -> Decimal:
        """Get tax rate based on shipping address state."""
        if not shipping_address:
            return Decimal('0.0825')  # Default 8.25%

        state = shipping_address.get('state', '').upper()

        # US state tax rates (simplified - real implementation would use tax API)
        state_tax_rates = {
            'CA': Decimal('0.0725'),
            'TX': Decimal('0.0625'),
            'NY': Decimal('0.08'),
            'FL': Decimal('0.06'),
            'WA': Decimal('0.065'),
            'IL': Decimal('0.0625'),
            'PA': Decimal('0.06'),
            'OH': Decimal('0.0575'),
            'GA': Decimal('0.04'),
            'NC': Decimal('0.0475'),
            'MI': Decimal('0.06'),
            'NJ': Decimal('0.06625'),
            'VA': Decimal('0.053'),
            'AZ': Decimal('0.056'),
            'MA': Decimal('0.0625'),
            'TN': Decimal('0.07'),
            'IN': Decimal('0.07'),
            'MO': Decimal('0.04225'),
            'MD': Decimal('0.06'),
            'WI': Decimal('0.05'),
            'CO': Decimal('0.029'),
            'MN': Decimal('0.06875'),
            'SC': Decimal('0.06'),
            'AL': Decimal('0.04'),
            'LA': Decimal('0.0445'),
            'KY': Decimal('0.06'),
            'OR': Decimal('0.00'),  # No sales tax
            'OK': Decimal('0.045'),
            'CT': Decimal('0.0635'),
            'UT': Decimal('0.0485'),
            'IA': Decimal('0.06'),
            'NV': Decimal('0.0685'),
            'AR': Decimal('0.065'),
            'MS': Decimal('0.07'),
            'KS': Decimal('0.065'),
            'NM': Decimal('0.05125'),
            'NE': Decimal('0.055'),
            'WV': Decimal('0.06'),
            'ID': Decimal('0.06'),
            'HI': Decimal('0.04'),
            'NH': Decimal('0.00'),  # No sales tax
            'ME': Decimal('0.055'),
            'MT': Decimal('0.00'),  # No sales tax
            'RI': Decimal('0.07'),
            'DE': Decimal('0.00'),  # No sales tax
            'SD': Decimal('0.045'),
            'ND': Decimal('0.05'),
            'AK': Decimal('0.00'),  # No state sales tax
            'VT': Decimal('0.06'),
            'DC': Decimal('0.06'),
            'WY': Decimal('0.04'),
        }

        return state_tax_rates.get(state, Decimal('0.0825'))

    @staticmethod
    def _calculate_shipping(cart: Cart, shipping_address: dict = None) -> Decimal:
        """Calculate shipping cost based on cart and destination."""
        subtotal = cart.subtotal
        item_count = cart.item_count

        # Free shipping for orders over $50
        if subtotal >= Decimal('50.00'):
            return Decimal('0.00')

        # Base shipping rates
        if item_count <= 2:
            base_shipping = Decimal('5.99')
        elif item_count <= 5:
            base_shipping = Decimal('7.99')
        else:
            base_shipping = Decimal('9.99')

        # Add surcharge for certain states (Alaska, Hawaii)
        if shipping_address:
            state = shipping_address.get('state', '').upper()
            if state in ['AK', 'HI']:
                base_shipping += Decimal('5.00')

        return base_shipping

    @staticmethod
    def validate_cart_for_checkout(cart: Cart) -> list:
        """Validate cart items for checkout. Returns list of validation errors."""
        errors = []

        if cart.is_empty:
            errors.append("Cart is empty.")
            return errors

        for item in cart.items.select_related('product', 'variant').all():
            # Check product is still active
            if item.product.status != Product.Status.ACTIVE:
                errors.append(f"'{item.product.name}' is no longer available.")
                continue

            # Check variant is still active
            if item.variant and not item.variant.is_active:
                errors.append(f"'{item.product.name}' variant is no longer available.")
                continue

            # Check stock availability
            available_stock = InventoryService.get_available_stock(
                item.product, item.variant
            )
            if item.quantity > available_stock:
                if available_stock == 0:
                    errors.append(f"'{item.product.name}' is out of stock.")
                else:
                    errors.append(
                        f"Only {available_stock} of '{item.product.name}' available."
                    )

        return errors
