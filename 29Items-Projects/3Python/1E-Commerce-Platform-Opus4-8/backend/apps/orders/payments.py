"""Payment gateway abstraction.

The checkout service depends on the `PaymentGateway` interface, not a concrete
provider, so the provider is swappable via the PAYMENT_GATEWAY setting and is
trivially mockable in tests. Every charge carries an idempotency key so that a
retried request never double-charges (ARCHITECTURE §2.5, TECH-NOTES §3.6).
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from decimal import Decimal
from typing import Protocol

from django.conf import settings


@dataclass(frozen=True)
class PaymentResult:
    success: bool
    payment_intent_id: str
    error: str | None = None


class PaymentError(Exception):
    """Raised when a charge is declined or the gateway fails."""


class PaymentGateway(Protocol):
    def charge(
        self, *, token: str, amount: Decimal, currency: str, idempotency_key: str
    ) -> PaymentResult: ...


class FakePaymentGateway:
    """Deterministic, offline gateway for local/dev/test.

    A token of "tok_decline" is rejected; everything else succeeds. The
    returned intent id is derived from the idempotency key, so repeating the
    same checkout yields the same intent id (true idempotency).
    """

    def charge(
        self, *, token: str, amount: Decimal, currency: str, idempotency_key: str
    ) -> PaymentResult:
        if token == "tok_decline":
            return PaymentResult(False, "", error="card_declined")
        digest = hashlib.sha256(idempotency_key.encode()).hexdigest()[:24]
        return PaymentResult(True, f"pi_fake_{digest}")


class StripePaymentGateway:
    """Real Stripe integration. Requires STRIPE_SECRET_KEY and network access."""

    def charge(
        self, *, token: str, amount: Decimal, currency: str, idempotency_key: str
    ) -> PaymentResult:
        import stripe  # imported lazily so the dep is only needed in prod

        stripe.api_key = settings.STRIPE_SECRET_KEY
        try:
            intent = stripe.PaymentIntent.create(
                amount=int(amount * 100),  # smallest currency unit
                currency=currency.lower(),
                payment_method=token,
                confirm=True,
                idempotency_key=idempotency_key,
            )
        except Exception as exc:  # pragma: no cover - network path
            return PaymentResult(False, "", error=str(exc))
        return PaymentResult(intent.status == "succeeded", intent.id)


def get_gateway() -> PaymentGateway:
    if settings.PAYMENT_GATEWAY == "stripe":
        return StripePaymentGateway()
    return FakePaymentGateway()
