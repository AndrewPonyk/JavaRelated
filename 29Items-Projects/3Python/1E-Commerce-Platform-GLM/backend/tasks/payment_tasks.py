"""
Payment Tasks

Celery tasks for payment processing.
"""

from celery import shared_task
from django.db import transaction
import stripe
import logging

logger = logging.getLogger("backend")

# Type hints - use string annotations to avoid import issues
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from backend.core.models import Order


@shared_task(bind=True)
def process_payment(self, order_id: str, payment_token: str = None) -> dict:
    """
    Process payment for an order.

    Args:
        order_id: The order ID
        payment_token: Stripe payment token

    Returns:
        Dictionary with payment result
    """
    from backend.core.models import Order
    from django.conf import settings

    try:
        order = Order.objects.get(id=order_id)

        if order.payment_method == "stripe":
            return _process_stripe_payment(order, payment_token or order.payment_data)
        elif order.payment_method == "paypal":
            return _process_paypal_payment(order)
        elif order.payment_method == "cod":
            return _process_cod_payment(order)
        else:
            return {"success": False, "error": "Unknown payment method"}

    except Exception as e:
        logger.error(f"Payment processing failed for order {order_id}: {e}")
        # Retry if recoverable error
        if self.request.retries < self.max_retries:
            raise self.retry(exc=e, countdown=60)
        return {"success": False, "error": str(e)}


def _process_stripe_payment(order: "Order", payment_token: dict) -> dict:
    """Process Stripe payment."""
    stripe.api_key = order.payment_data.get("stripe_secret_key")

    try:
        # Create payment intent
        intent = stripe.PaymentIntent.create(
            amount=int(order.total * 100),  # Convert to cents
            currency="usd",
            payment_method=payment_token,
            confirm=True,
        )

        if intent.status == "succeeded":
            with transaction.atomic():
                order.payment_status = "paid"
                order.payment_id = intent.id
                order.confirm()

            logger.info(f"Stripe payment successful for order {order.id}")
            return {"success": True, "payment_id": intent.id}
        else:
            return {"success": False, "error": "Payment failed"}

    except stripe.error.CardError as e:
        logger.error(f"Stripe card error: {e}")
        return {"success": False, "error": str(e)}
    except Exception as e:
        logger.error(f"Stripe payment error: {e}")
        return {"success": False, "error": str(e)}


def _process_paypal_payment(order: "Order") -> dict:
    """Process PayPal payment."""
    # Note: This requires paypalrestsdk package and PayPal API credentials
    # Install: pip install paypalrestsdk

    try:
        import paypalrestsdk

        # Configure PayPal SDK
        from django.conf import settings

        paypalrestsdk.configure({
            "mode": getattr(settings, "PAYPAL_MODE", "sandbox"),  # sandbox or live
            "client_id": getattr(settings, "PAYPAL_CLIENT_ID", ""),
            "client_secret": getattr(settings, "PAYPAL_CLIENT_SECRET", ""),
        })

        # Create payment
        payment = paypalrestsdk.Payment({
            "intent": "sale",
            "payer": {
                "payment_method": "paypal"
            },
            "redirect_urls": {
                "return_url": f"{getattr(settings, 'SITE_URL')}/payment/paypal/return/",
                "cancel_url": f"{getattr(settings, 'SITE_URL')}/payment/paypal/cancel/"
            },
            "transactions": [{
                "item_list": {
                    "items": [{
                        "name": item.product.name,
                        "sku": item.product.sku or str(item.product.id),
                        "price": str(item.unit_price),
                        "currency": "USD",
                        "quantity": item.quantity
                    } for item in order.items.all()]
                },
                "amount": {
                    "total": str(order.total),
                    "currency": "USD"
                },
                "description": f"Order {order.order_number}"
            }]
        })

        if payment.create():
            # Store PayPal payment ID
            order.payment_data["paypal_payment_id"] = payment.id
            order.save(update_fields=["payment_data"])

            # Get approval URL
            for link in payment.links:
                if link.method == "REDIRECT":
                    approval_url = link.href
                    return {
                        "success": True,
                        "requires_action": True,
                        "approval_url": approval_url,
                        "payment_id": payment.id
                    }

        return {"success": False, "error": "Failed to create PayPal payment"}

    except ImportError:
        logger.error("paypalrestsdk not installed. Install with: pip install paypalrestsdk")
        return {"success": False, "error": "PayPal not configured"}
    except Exception as e:
        logger.error(f"PayPal payment error: {e}")
        return {"success": False, "error": str(e)}


def _process_cod_payment(order: "Order") -> dict:
    """Process cash on delivery payment."""
    with transaction.atomic():
        order.payment_status = "pending"
        order.confirm()

    logger.info(f"Cash on delivery payment confirmed for order {order.id}")
    return {"success": True}


@shared_task(bind=True)
def process_refund(self, order_id: str) -> dict:
    """
    Process refund for an order.

    Args:
        order_id: The order ID

    Returns:
        Dictionary with refund result
    """
    from backend.core.models import Order

    try:
        order = Order.objects.get(id=order_id)

        if order.payment_method == "stripe" and order.payment_id:
            return _process_stripe_refund(order)
        elif order.payment_method == "paypal":
            return _process_paypal_refund(order)
        else:
            # Mark as refunded for COD
            order.refund()
            return {"success": True}

    except Exception as e:
        logger.error(f"Refund processing failed for order {order_id}: {e}")
        return {"success": False, "error": str(e)}


def _process_stripe_refund(order: "Order") -> dict:
    """Process Stripe refund."""
    stripe.api_key = order.payment_data.get("stripe_secret_key")

    try:
        refund = stripe.Refund.create(
            payment_intent=order.payment_id,
            amount=int(order.total * 100),  # Convert to cents
        )

        if refund.status == "succeeded":
            order.refund()
            logger.info(f"Stripe refund successful for order {order.id}")
            return {"success": True, "refund_id": refund.id}
        else:
            return {"success": False, "error": "Refund failed"}

    except Exception as e:
        logger.error(f"Stripe refund error: {e}")
        return {"success": False, "error": str(e)}


def _process_paypal_refund(order: "Order") -> dict:
    """Process PayPal refund."""
    # Note: This requires paypalrestsdk package and PayPal API credentials

    try:
        import paypalrestsdk

        # Configure PayPal SDK
        from django.conf import settings

        paypalrestsdk.configure({
            "mode": getattr(settings, "PAYPAL_MODE", "sandbox"),
            "client_id": getattr(settings, "PAYPAL_CLIENT_ID", ""),
            "client_secret": getattr(settings, "PAYPAL_CLIENT_SECRET", ""),
        })

        # Get the sale ID from payment data (stored during capture)
        sale_id = order.payment_data.get("paypal_sale_id")
        if not sale_id:
            return {"success": False, "error": "PayPal sale ID not found"}

        # Get the sale
        sale = paypalrestsdk.Sale.find(sale_id)

        # Refund the sale
        refund = sale.refund({
            "amount": {
                "total": str(order.total),
                "currency": "USD"
            }
        })

        if refund.success():
            order.refund()
            logger.info(f"PayPal refund successful for order {order.id}")
            return {"success": True, "refund_id": refund.id}
        else:
            return {"success": False, "error": refund.error}

    except ImportError:
        logger.error("paypalrestsdk not installed")
        return {"success": False, "error": "PayPal not configured"}
    except Exception as e:
        logger.error(f"PayPal refund error: {e}")
        return {"success": False, "error": str(e)}
