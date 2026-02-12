"""
Payment Service

Business logic for payment processing using Stripe.
"""

from typing import Dict, Any, Optional
from decimal import Decimal
from django.db import transaction
from django.conf import settings
import stripe
import logging

logger = logging.getLogger("backend")


class PaymentService:
    """Service class for payment-related operations."""

    def __init__(self):
        """Initialize Stripe API key."""
        stripe.api_key = getattr(settings, "STRIPE_SECRET_KEY", "")

    def create_payment_intent(
        self,
        amount: Decimal,
        currency: str = "usd",
        metadata: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Create a Stripe PaymentIntent.

        Args:
            amount: Payment amount in dollars
            currency: Currency code (default: usd)
            metadata: Optional metadata for the payment

        Returns:
            Dictionary with payment intent details

        Raises:
            ValueError: If payment intent creation fails
        """
        try:
            # Convert to cents for Stripe
            amount_cents = int(amount * 100)

            intent = stripe.PaymentIntent.create(
                amount=amount_cents,
                currency=currency,
                metadata=metadata or {},
                automatic_payment_methods={
                    "enabled": True,
                },
            )

            return {
                "id": intent.id,
                "client_secret": intent.client_secret,
                "amount": amount,
                "currency": currency,
                "status": intent.status,
                "created_at": intent.created,
            }

        except stripe.error.StripeError as e:
            logger.error(f"Stripe payment intent creation failed: {e}")
            raise ValueError(f"Payment processing failed: {str(e)}")

    def confirm_payment_intent(
        self,
        payment_intent_id: str,
    ) -> Dict[str, Any]:
        """
        Confirm and retrieve payment intent details.

        Args:
            payment_intent_id: The Stripe PaymentIntent ID

        Returns:
            Dictionary with payment intent details

        Raises:
            ValueError: If payment intent retrieval fails
        """
        try:
            intent = stripe.PaymentIntent.retrieve(payment_intent_id)

            return {
                "id": intent.id,
                "amount": Decimal(intent.amount) / 100,
                "currency": intent.currency,
                "status": intent.status,
                "created_at": intent.created,
                "metadata": intent.metadata,
            }

        except stripe.error.StripeError as e:
            logger.error(f"Stripe payment intent retrieval failed: {e}")
            raise ValueError(f"Failed to retrieve payment: {str(e)}")

    def process_payment(
        self,
        payment_method_id: str,
        amount: Decimal,
        currency: str = "usd",
        description: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Process a payment using a payment method.

        Args:
            payment_method_id: Stripe PaymentMethod ID
            amount: Payment amount in dollars
            currency: Currency code
            description: Payment description
            metadata: Optional metadata

        Returns:
            Dictionary with payment details

        Raises:
            ValueError: If payment fails
        """
        try:
            amount_cents = int(amount * 100)

            payment_intent = stripe.PaymentIntent.create(
                amount=amount_cents,
                currency=currency,
                payment_method=payment_method_id,
                confirm=True,
                description=description,
                metadata=metadata or {},
                return_url="https://your-domain.com/return",
            )

            if payment_intent.status == "succeeded":
                return {
                    "id": payment_intent.id,
                    "status": "succeeded",
                    "amount": amount,
                    "currency": currency,
                }

            raise ValueError(f"Payment failed with status: {payment_intent.status}")

        except stripe.error.CardError as e:
            logger.error(f"Stripe card error: {e}")
            raise ValueError(f"Card declined: {str(e)}")

        except stripe.error.StripeError as e:
            logger.error(f"Stripe payment error: {e}")
            raise ValueError(f"Payment failed: {str(e)}")

    def create_refund(
        self,
        payment_intent_id: str,
        amount: Optional[Decimal] = None,
        reason: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Create a refund for a payment.

        Args:
            payment_intent_id: The PaymentIntent ID to refund
            amount: Refund amount in dollars (None for full refund)
            reason: Reason for the refund

        Returns:
            Dictionary with refund details

        Raises:
            ValueError: If refund fails
        """
        try:
            refund_params = {
                "payment_intent": payment_intent_id,
            }

            if amount is not None:
                refund_params["amount"] = int(amount * 100)

            if reason:
                refund_params["reason"] = reason

            refund = stripe.Refund.create(**refund_params)

            return {
                "id": refund.id,
                "amount": Decimal(refund.amount) / 100,
                "currency": refund.currency,
                "status": refund.status,
                "created_at": refund.created,
            }

        except stripe.error.StripeError as e:
            logger.error(f"Stripe refund failed: {e}")
            raise ValueError(f"Refund failed: {str(e)}")

    def get_payment_method(self, payment_method_id: str) -> Dict[str, Any]:
        """
        Retrieve payment method details.

        Args:
            payment_method_id: Stripe PaymentMethod ID

        Returns:
            Dictionary with payment method details

        Raises:
            ValueError: If retrieval fails
        """
        try:
            pm = stripe.PaymentMethod.retrieve(payment_method_id)

            return {
                "id": pm.id,
                "type": pm.type,
                "card": {
                    "brand": pm.card.brand,
                    "last4": pm.card.last4,
                    "exp_month": pm.card.exp_month,
                    "exp_year": pm.card.exp_year,
                } if pm.type == "card" else None,
            }

        except stripe.error.StripeError as e:
            logger.error(f"Stripe payment method retrieval failed: {e}")
            raise ValueError(f"Failed to retrieve payment method: {str(e)}")

    def attach_payment_method(
        self,
        payment_method_id: str,
        customer_id: str,
    ) -> Dict[str, Any]:
        """
        Attach payment method to a customer.

        Args:
            payment_method_id: Stripe PaymentMethod ID
            customer_id: Stripe Customer ID

        Returns:
            Updated payment method details

        Raises:
            ValueError: If attachment fails
        """
        try:
            pm = stripe.PaymentMethod.attach(
                payment_method_id,
                customer=customer_id,
            )

            return {
                "id": pm.id,
                "customer": pm.customer,
                "type": pm.type,
            }

        except stripe.error.StripeError as e:
            logger.error(f"Stripe payment method attachment failed: {e}")
            raise ValueError(f"Failed to attach payment method: {str(e)}")

    def create_customer(
        self,
        email: str,
        name: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Create a Stripe customer.

        Args:
            email: Customer email
            name: Customer name
            metadata: Optional metadata

        Returns:
            Dictionary with customer details

        Raises:
            ValueError: If customer creation fails
        """
        try:
            customer = stripe.Customer.create(
                email=email,
                name=name,
                metadata=metadata or {},
            )

            return {
                "id": customer.id,
                "email": customer.email,
                "name": customer.name,
                "created_at": customer.created,
            }

        except stripe.error.StripeError as e:
            logger.error(f"Stripe customer creation failed: {e}")
            raise ValueError(f"Failed to create customer: {str(e)}")

    def setup_intent(
        self,
        customer_id: str,
        payment_method_types: Optional[list[str]] = None,
    ) -> Dict[str, Any]:
        """
        Create a SetupIntent for saving payment methods.

        Args:
            customer_id: Stripe Customer ID
            payment_method_types: List of payment method types

        Returns:
            Dictionary with setup intent details

        Raises:
            ValueError: If setup intent creation fails
        """
        try:
            intent = stripe.SetupIntent.create(
                customer=customer_id,
                payment_method_types=payment_method_types or ["card"],
                payment_method_options={
                    "card": {"request_three_d_secure": "automatic"},
                },
            )

            return {
                "id": intent.id,
                "client_secret": intent.client_secret,
                "status": intent.status,
            }

        except stripe.error.StripeError as e:
            logger.error(f"Stripe setup intent failed: {e}")
            raise ValueError(f"Failed to create setup intent: {str(e)}")


# PayPal payment service (stub for future implementation)
class PayPalPaymentService:
    """Service class for PayPal payments."""

    def __init__(self):
        """Initialize PayPal credentials."""
        self.client_id = getattr(settings, "PAYPAL_CLIENT_ID", "")
        self.client_secret = getattr(settings, "PAYPAL_CLIENT_SECRET", "")
        self.mode = getattr(settings, "PAYPAL_MODE", "sandbox")

    def create_order(
        self,
        amount: Decimal,
        currency: str = "USD",
    ) -> Dict[str, Any]:
        """
        Create a PayPal order.

        Args:
            amount: Payment amount
            currency: Currency code

        Returns:
            Dictionary with order details
        """
        # TODO: Implement PayPal API integration
        return {
            "id": "PAYPAL_ORDER_ID",
            "status": "CREATED",
            "links": [],
        }

    def capture_payment(
        self,
        order_id: str,
    ) -> Dict[str, Any]:
        """
        Capture payment for a PayPal order.

        Args:
            order_id: PayPal Order ID

        Returns:
            Dictionary with capture details
        """
        # TODO: Implement PayPal capture API
        return {
            "id": "CAPTURE_ID",
            "status": "COMPLETED",
            "amount": {
                "value": "0.00",
                "currency_code": "USD",
            },
        }
