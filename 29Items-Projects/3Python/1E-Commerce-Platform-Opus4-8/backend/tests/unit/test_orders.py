"""Order checkout, payment, idempotency, cancellation, and email tests."""

import pytest
from django.core import mail

from apps.cart import services as cart_services
from apps.orders import services as order_services
from apps.orders.models import Order
from apps.orders.payments import FakePaymentGateway
from apps.orders.tasks import send_order_confirmation_email
from common.utils.exceptions import CartEmptyError

pytestmark = pytest.mark.django_db


def _cart_with_item(user, product, qty=2):
    cart = cart_services.get_or_create_active_cart(user=user)
    cart_services.add_item(cart=cart, product_id=product.id, quantity=qty)
    return cart


def test_checkout_creates_paid_order_and_decrements_stock(user, product_in_stock):
    cart = _cart_with_item(user, product_in_stock, qty=2)
    order = order_services.place_order(user=user, cart=cart, payment_token="tok_visa")

    assert order.status == Order.Status.PAID
    assert order.total_amount == product_in_stock.price * 2
    product_in_stock.stock.refresh_from_db()
    assert product_in_stock.stock.quantity_on_hand == 8
    assert product_in_stock.stock.quantity_reserved == 0
    cart.refresh_from_db()
    assert cart.is_active is False


def test_checkout_empty_cart_raises(user):
    cart = cart_services.get_or_create_active_cart(user=user)
    with pytest.raises(CartEmptyError):
        order_services.place_order(user=user, cart=cart, payment_token="tok_visa")


def test_payment_declined(user, product_in_stock):
    cart = _cart_with_item(user, product_in_stock)
    from apps.orders.services import PaymentDeclinedError

    with pytest.raises(PaymentDeclinedError):
        order_services.place_order(user=user, cart=cart, payment_token="tok_decline")
    # No order persisted, stock reservation rolled back.
    assert not Order.objects.exists()
    product_in_stock.stock.refresh_from_db()
    assert product_in_stock.stock.quantity_reserved == 0


def test_checkout_idempotent_on_same_cart(user, product_in_stock):
    cart = _cart_with_item(user, product_in_stock)
    o1 = order_services.place_order(user=user, cart=cart, payment_token="tok_visa")
    o2 = order_services.place_order(user=user, cart=cart, payment_token="tok_visa")
    assert o1.id == o2.id
    assert Order.objects.count() == 1


def test_checkout_via_api(auth_client, user, product_in_stock):
    _cart_with_item(user, product_in_stock, qty=1)
    resp = auth_client.post(
        "/api/v1/orders/checkout/", {"payment_token": "tok_visa"}, format="json"
    )
    assert resp.status_code == 201
    assert resp.data["status"] == "paid"
    assert resp.data["item_count"] == 1


def test_checkout_no_cart_returns_404(auth_client):
    resp = auth_client.post("/api/v1/orders/checkout/", {}, format="json")
    assert resp.status_code == 404


def test_order_list_and_cancel(auth_client, user, product_in_stock):
    cart = _cart_with_item(user, product_in_stock, qty=1)
    order = order_services.place_order(user=user, cart=cart, payment_token="tok_visa")

    lst = auth_client.get("/api/v1/orders/")
    assert lst.status_code == 200
    assert lst.data["count"] == 1

    cancel = auth_client.post(f"/api/v1/orders/{order.number}/cancel/")
    assert cancel.status_code == 200
    assert cancel.data["status"] == "cancelled"


def test_confirmation_email_idempotent(user, product_in_stock):
    cart = _cart_with_item(user, product_in_stock, qty=1)
    order = order_services.place_order(user=user, cart=cart, payment_token="tok_visa")
    mail.outbox.clear()

    send_order_confirmation_email(order.id)
    assert len(mail.outbox) == 1
    # Second delivery is a no-op (already sent).
    send_order_confirmation_email(order.id)
    assert len(mail.outbox) == 1


def test_fake_gateway_is_idempotent():
    gw = FakePaymentGateway()
    from decimal import Decimal

    r1 = gw.charge(token="tok_visa", amount=Decimal("10"), currency="USD", idempotency_key="k1")
    r2 = gw.charge(token="tok_visa", amount=Decimal("10"), currency="USD", idempotency_key="k1")
    assert r1.success and r1.payment_intent_id == r2.payment_intent_id
