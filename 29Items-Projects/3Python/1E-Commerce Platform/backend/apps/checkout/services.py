"""Checkout service layer for E-Commerce Platform."""
import stripe
from decimal import Decimal
from typing import Optional

from django.conf import settings
from django.db import transaction
from django.utils import timezone

from apps.cart.models import Cart
from apps.cart.services import CartService
from apps.inventory.services import InventoryService
from apps.users.models import Address

from .models import Coupon, CouponUsage, Order, OrderItem, OrderStatusHistory


class CheckoutService:
    """Service class for checkout-related business logic."""

    @staticmethod
    @transaction.atomic
    def initiate_checkout(user, checkout_data: dict) -> dict:
        """Initialize checkout process and create payment intent."""
        # Get user's cart
        cart = Cart.objects.filter(user=user).first()
        if not cart or cart.is_empty:
            raise ValueError("Cart is empty.")

        # Validate cart items
        validation_errors = CartService.validate_cart_for_checkout(cart)
        if validation_errors:
            raise ValueError("; ".join(validation_errors))

        # Resolve addresses
        shipping_address = CheckoutService._resolve_address(
            user,
            checkout_data.get('shipping_address_id'),
            checkout_data.get('shipping_address')
        )

        if checkout_data.get('same_as_shipping', True):
            billing_address = shipping_address
        else:
            billing_address = CheckoutService._resolve_address(
                user,
                checkout_data.get('billing_address_id'),
                checkout_data.get('billing_address')
            )

        # Calculate totals with proper address
        cart_totals = CartService.calculate_cart_totals(cart, shipping_address)

        # Apply coupon if provided
        discount_amount = Decimal('0.00')
        coupon = None
        coupon_code = checkout_data.get('coupon_code')
        if coupon_code:
            try:
                coupon = CouponService.validate_coupon(
                    code=coupon_code,
                    user=user,
                    cart_total=cart_totals['subtotal']
                )
                discount_amount = CouponService.calculate_discount(
                    coupon, cart_totals['subtotal']
                )
                # Handle free shipping coupon
                if coupon.discount_type == Coupon.DiscountType.FREE_SHIPPING:
                    cart_totals['shipping_estimate'] = Decimal('0.00')
            except ValueError as e:
                raise ValueError(f"Coupon error: {str(e)}")

        final_total = (
            cart_totals['subtotal'] +
            cart_totals['tax_amount'] +
            cart_totals['shipping_estimate'] -
            discount_amount
        )

        # Create Stripe payment intent
        stripe.api_key = getattr(settings, 'STRIPE_SECRET_KEY', None)
        client_secret = None
        is_demo_mode = True

        # Check if we have a real Stripe key (not a placeholder)
        has_real_stripe_key = (
            stripe.api_key and
            stripe.api_key.startswith('sk_') and
            'xxx' not in stripe.api_key and
            len(stripe.api_key) > 20
        )

        if has_real_stripe_key:
            try:
                intent = stripe.PaymentIntent.create(
                    amount=int(final_total * 100),  # Stripe uses cents
                    currency='usd',
                    automatic_payment_methods={'enabled': True},
                    metadata={
                        'user_id': str(user.id),
                        'coupon_code': coupon_code or '',
                    }
                )
                client_secret = intent.client_secret
                is_demo_mode = False
            except stripe.error.StripeError as e:
                raise ValueError(f"Payment error: {str(e)}")
        else:
            # Development/demo mode - generate mock secret
            import uuid
            client_secret = f"pi_{uuid.uuid4().hex}_secret_{uuid.uuid4().hex[:24]}"

        # Store checkout session data
        checkout_session = {
            'shipping_address': shipping_address,
            'billing_address': billing_address,
            'coupon_code': coupon_code,
            'discount_amount': str(discount_amount),
            'subtotal': str(cart_totals['subtotal']),
            'tax_amount': str(cart_totals['tax_amount']),
            'shipping_amount': str(cart_totals['shipping_estimate']),
            'total': str(final_total),
        }

        # Store in cache for later retrieval
        from django.core.cache import cache
        cache_key = f"checkout_session:{user.id}"
        cache.set(cache_key, checkout_session, timeout=3600)  # 1 hour

        return {
            'subtotal': str(cart_totals['subtotal']),
            'tax_amount': str(cart_totals['tax_amount']),
            'shipping_amount': str(cart_totals['shipping_estimate']),
            'discount_amount': str(discount_amount),
            'total': str(final_total),
            'client_secret': client_secret,
            'shipping_address': shipping_address,
            'billing_address': billing_address,
            'is_demo_mode': is_demo_mode,
        }

    @staticmethod
    def _sanitize_address_field(value: str, max_length: int = 200) -> str:
        """Sanitize address field to prevent injection attacks."""
        import html
        if not value:
            return ''
        # Strip HTML tags, escape remaining HTML entities, and limit length
        sanitized = html.escape(str(value).strip())
        return sanitized[:max_length]

    @staticmethod
    def _resolve_address(user, address_id: Optional[int], address_data: Optional[dict]) -> dict:
        """Resolve address from ID or data."""
        if address_id:
            address = Address.objects.filter(id=address_id, user=user).first()
            if address:
                return {
                    'full_name': CheckoutService._sanitize_address_field(
                        f"{user.first_name} {user.last_name}".strip() or user.email
                    ),
                    'street_address': CheckoutService._sanitize_address_field(address.street_address),
                    'apartment': CheckoutService._sanitize_address_field(address.apartment or ''),
                    'city': CheckoutService._sanitize_address_field(address.city),
                    'state': CheckoutService._sanitize_address_field(address.state, 50),
                    'postal_code': CheckoutService._sanitize_address_field(address.postal_code, 20),
                    'country': CheckoutService._sanitize_address_field(address.country, 50),
                    'phone': CheckoutService._sanitize_address_field(address.phone or '', 30),
                }
        if address_data:
            # Validate required fields
            required = ['street_address', 'city', 'state', 'postal_code', 'country']
            for field in required:
                if not address_data.get(field):
                    raise ValueError(f"Address field '{field}' is required.")
            # Sanitize all address fields
            return {
                'full_name': CheckoutService._sanitize_address_field(address_data.get('full_name', '')),
                'street_address': CheckoutService._sanitize_address_field(address_data['street_address']),
                'apartment': CheckoutService._sanitize_address_field(address_data.get('apartment', '')),
                'city': CheckoutService._sanitize_address_field(address_data['city']),
                'state': CheckoutService._sanitize_address_field(address_data['state'], 50),
                'postal_code': CheckoutService._sanitize_address_field(address_data['postal_code'], 20),
                'country': CheckoutService._sanitize_address_field(address_data['country'], 50),
                'phone': CheckoutService._sanitize_address_field(address_data.get('phone', ''), 30),
            }
        raise ValueError("Address is required.")

    @staticmethod
    @transaction.atomic
    def confirm_payment(user, payment_data: dict) -> Order:
        """Confirm payment and create order."""
        payment_intent_id = payment_data.get('payment_intent_id')

        if not payment_intent_id:
            raise ValueError("Payment intent ID is required.")

        # Verify payment with Stripe if configured and payment_intent_id is a real Stripe ID
        stripe.api_key = getattr(settings, 'STRIPE_SECRET_KEY', None)
        is_real_payment_intent = payment_intent_id.startswith('pi_') and '_secret_' not in payment_intent_id

        if stripe.api_key and is_real_payment_intent:
            try:
                intent = stripe.PaymentIntent.retrieve(payment_intent_id)
                if intent.status != 'succeeded':
                    raise ValueError("Payment not successful.")
            except stripe.error.StripeError as e:
                raise ValueError(f"Payment verification failed: {str(e)}")

        # Get cart
        cart = Cart.objects.filter(user=user).first()
        if not cart or cart.is_empty:
            raise ValueError("Cart is empty.")

        # Validate cart again
        validation_errors = CartService.validate_cart_for_checkout(cart)
        if validation_errors:
            raise ValueError("; ".join(validation_errors))

        # Get checkout session from cache
        from django.core.cache import cache
        cache_key = f"checkout_session:{user.id}"
        checkout_session = cache.get(cache_key)

        if not checkout_session:
            raise ValueError("Checkout session expired. Please start checkout again.")

        # Reserve inventory for all items
        for cart_item in cart.items.select_related('product', 'variant').all():
            reserved = InventoryService.reserve_stock(
                product=cart_item.product,
                variant=cart_item.variant,
                quantity=cart_item.quantity,
                reference_type='order',
                reference_id=str(cart_item.id)
            )
            if not reserved:
                raise ValueError(f"Unable to reserve stock for '{cart_item.product.name}'.")

        # Create order
        order = Order.objects.create(
            user=user,
            email=user.email,
            status=Order.Status.CONFIRMED,
            payment_status=Order.PaymentStatus.CAPTURED,
            subtotal=Decimal(checkout_session['subtotal']),
            tax_amount=Decimal(checkout_session['tax_amount']),
            shipping_amount=Decimal(checkout_session['shipping_amount']),
            discount_amount=Decimal(checkout_session['discount_amount']),
            total=Decimal(checkout_session['total']),
            payment_intent_id=payment_intent_id,
            shipping_address=checkout_session['shipping_address'],
            billing_address=checkout_session['billing_address'],
            customer_notes=payment_data.get('notes', ''),
            paid_at=timezone.now(),
        )

        # Create order items and deduct inventory
        for cart_item in cart.items.select_related('product', 'variant').all():
            OrderItem.objects.create(
                order=order,
                product=cart_item.product,
                variant=cart_item.variant,
                vendor=cart_item.product.vendor,
                product_name=cart_item.product.name,
                product_sku=cart_item.product.sku,
                variant_name=cart_item.variant.name if cart_item.variant else '',
                unit_price=cart_item.unit_price,
                quantity=cart_item.quantity,
                line_total=cart_item.line_total,
            )

            # Deduct inventory (converts reservation to sale)
            InventoryService.deduct_stock(
                product=cart_item.product,
                variant=cart_item.variant,
                quantity=cart_item.quantity,
                reference_type='order',
                reference_id=str(order.id)
            )

        # Record coupon usage if applicable
        coupon_code = checkout_session.get('coupon_code')
        if coupon_code:
            try:
                coupon = Coupon.objects.get(code__iexact=coupon_code)
                CouponUsage.objects.create(
                    coupon=coupon,
                    user=user,
                    order=order,
                    discount_amount=Decimal(checkout_session['discount_amount'])
                )
                coupon.usage_count += 1
                coupon.save(update_fields=['usage_count'])
            except Coupon.DoesNotExist:
                pass

        # Record status change
        OrderStatusHistory.objects.create(
            order=order,
            status=Order.Status.CONFIRMED,
            note='Order confirmed and payment captured.',
            created_by=user,
        )

        # Clear cart
        CartService.clear_cart(cart)

        # Clear checkout session
        cache.delete(cache_key)

        # Trigger async tasks
        from celery_tasks.email_tasks import send_order_confirmation_email
        from celery_tasks.inventory_tasks import update_inventory_after_order
        send_order_confirmation_email.delay(str(order.id))
        update_inventory_after_order.delay(str(order.id))

        return order

    @staticmethod
    @transaction.atomic
    def cancel_order(order: Order, user, reason: str = '') -> Order:
        """Cancel an order if eligible."""
        cancellable_statuses = [Order.Status.PENDING, Order.Status.CONFIRMED]
        if order.status not in cancellable_statuses:
            raise ValueError("Order cannot be cancelled at this stage.")

        # Process refund if payment was captured
        if order.payment_status == Order.PaymentStatus.CAPTURED:
            stripe.api_key = getattr(settings, 'STRIPE_SECRET_KEY', None)
            if stripe.api_key and order.payment_intent_id:
                try:
                    stripe.Refund.create(payment_intent=order.payment_intent_id)
                    order.payment_status = Order.PaymentStatus.REFUNDED
                except stripe.error.StripeError as e:
                    raise ValueError(f"Refund failed: {str(e)}")

        order.status = Order.Status.CANCELLED
        order.save()

        OrderStatusHistory.objects.create(
            order=order,
            status=Order.Status.CANCELLED,
            note=f'Order cancelled by customer. Reason: {reason}' if reason else 'Order cancelled by customer.',
            created_by=user,
        )

        # Restore inventory
        from celery_tasks.inventory_tasks import restore_inventory_after_cancellation
        restore_inventory_after_cancellation.delay(str(order.id))

        return order

    @staticmethod
    @transaction.atomic
    def update_order_status(order: Order, new_status: str, user=None, note: str = '') -> Order:
        """Update order status with history tracking."""
        valid_transitions = {
            Order.Status.PENDING: [Order.Status.CONFIRMED, Order.Status.CANCELLED],
            Order.Status.CONFIRMED: [Order.Status.PROCESSING, Order.Status.CANCELLED],
            Order.Status.PROCESSING: [Order.Status.SHIPPED, Order.Status.CANCELLED],
            Order.Status.SHIPPED: [Order.Status.DELIVERED],
            Order.Status.DELIVERED: [Order.Status.REFUNDED],
        }

        allowed = valid_transitions.get(order.status, [])
        if new_status not in allowed:
            raise ValueError(f"Cannot transition from {order.status} to {new_status}.")

        old_status = order.status
        order.status = new_status

        # Update timestamps
        if new_status == Order.Status.SHIPPED:
            order.shipped_at = timezone.now()
        elif new_status == Order.Status.DELIVERED:
            order.delivered_at = timezone.now()

        order.save()

        OrderStatusHistory.objects.create(
            order=order,
            status=new_status,
            note=note or f'Status changed from {old_status} to {new_status}.',
            created_by=user,
        )

        # Send notifications based on status
        if new_status == Order.Status.SHIPPED:
            from celery_tasks.email_tasks import send_shipping_notification_email
            tracking_number = note if 'tracking' in note.lower() else None
            send_shipping_notification_email.delay(str(order.id), tracking_number)

        return order


class CouponService:
    """Service class for coupon-related business logic."""

    @staticmethod
    def validate_coupon(code: str, user, cart_total: Decimal) -> Coupon:
        """Validate a coupon code."""
        try:
            coupon = Coupon.objects.get(code__iexact=code, is_active=True)
        except Coupon.DoesNotExist:
            raise ValueError("Invalid coupon code.")

        now = timezone.now()
        if coupon.valid_from > now:
            raise ValueError("Coupon is not yet valid.")
        if coupon.valid_until < now:
            raise ValueError("Coupon has expired.")

        if coupon.usage_limit and coupon.usage_count >= coupon.usage_limit:
            raise ValueError("Coupon usage limit reached.")

        if coupon.minimum_order_amount and cart_total < coupon.minimum_order_amount:
            raise ValueError(
                f"Minimum order amount of ${coupon.minimum_order_amount} required."
            )

        # Check per-user usage limit
        user_usage = CouponUsage.objects.filter(coupon=coupon, user=user).count()
        if user_usage >= coupon.usage_limit_per_user:
            raise ValueError("You have already used this coupon the maximum number of times.")

        return coupon

    @staticmethod
    def calculate_discount(coupon: Coupon, cart_total: Decimal) -> Decimal:
        """Calculate discount amount for a coupon."""
        if coupon.discount_type == Coupon.DiscountType.PERCENTAGE:
            discount = cart_total * (coupon.discount_value / 100)
        elif coupon.discount_type == Coupon.DiscountType.FIXED:
            discount = min(coupon.discount_value, cart_total)  # Don't exceed cart total
        else:  # Free shipping
            return Decimal('0.00')  # Handled separately in checkout

        if coupon.maximum_discount_amount:
            discount = min(discount, coupon.maximum_discount_amount)

        return discount.quantize(Decimal('0.01'))

    @staticmethod
    def get_available_coupons(user) -> list:
        """Get available coupons for a user."""
        now = timezone.now()
        coupons = Coupon.objects.filter(
            is_active=True,
            valid_from__lte=now,
            valid_until__gte=now
        )

        available = []
        for coupon in coupons:
            # Check global usage limit
            if coupon.usage_limit and coupon.usage_count >= coupon.usage_limit:
                continue

            # Check per-user usage limit
            user_usage = CouponUsage.objects.filter(coupon=coupon, user=user).count()
            if user_usage >= coupon.usage_limit_per_user:
                continue

            available.append({
                'code': coupon.code,
                'discount_type': coupon.discount_type,
                'discount_value': str(coupon.discount_value),
                'minimum_order_amount': str(coupon.minimum_order_amount) if coupon.minimum_order_amount else None,
                'maximum_discount_amount': str(coupon.maximum_discount_amount) if coupon.maximum_discount_amount else None,
            })

        return available
