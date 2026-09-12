"""Integration test: full vendor->product->cart->checkout->order journey.

Runs entirely through the public API against the real (SQLite test) DB, with
eager Celery so the post-commit email + recommendation tasks execute inline.
"""

import pytest
from django.core import mail

from apps.inventory.models import StockItem
from apps.orders.models import Order
from apps.recommendations.models import Interaction

# transaction=True so transaction.on_commit() side effects (email, reco signal)
# actually fire — they don't under the default wrapped-transaction test DB.
pytestmark = [pytest.mark.django_db(transaction=True), pytest.mark.integration]


def _register_and_login(api_client, email, role=None):
    payload = {"email": email, "password": "Sup3r-Secret-99"}
    if role:
        payload["role"] = role
    api_client.post("/api/v1/auth/register/", payload, format="json")
    tokens = api_client.post(
        "/api/v1/auth/token/",
        {"email": email, "password": "Sup3r-Secret-99"},
        format="json",
    ).data
    api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {tokens['access']}")
    return tokens


def test_full_checkout_journey(api_client):
    from apps.vendors.models import Vendor
    from tests.conftest import CategoryFactory

    # --- Vendor onboards and lists a product ---
    _register_and_login(api_client, "vendor@example.com", role="vendor")
    api_client.post("/api/v1/vendors/", {"name": "Acme"}, format="json")
    # Approve the vendor (admin action simulated directly).
    Vendor.objects.filter(owner__email="vendor@example.com").update(status=Vendor.Status.APPROVED)

    cat = CategoryFactory()
    create = api_client.post(
        "/api/v1/catalog/products/",
        {
            "sku": "tee-1",
            "name": "T-Shirt",
            "price": "25.00",
            "category_id": cat.id,
            "status": "active",
        },
        format="json",
    )
    assert create.status_code == 201, create.data
    product_id = create.data["id"]

    # Stock the product (vendor sets inventory; done directly here).
    StockItem.objects.filter(product_id=product_id).update(quantity_on_hand=5)

    # --- Customer shops and checks out ---
    api_client.credentials()  # drop vendor auth
    _register_and_login(api_client, "buyer@example.com")

    add = api_client.post(
        "/api/v1/cart/items/",
        {"product_id": product_id, "quantity": 2},
        format="json",
    )
    assert add.status_code == 201
    assert add.data["total"] == "50.00"

    mail.outbox.clear()
    checkout = api_client.post(
        "/api/v1/orders/checkout/", {"payment_token": "tok_visa"}, format="json"
    )
    assert checkout.status_code == 201
    number = checkout.data["number"]

    # --- Verify side effects ---
    order = Order.objects.get(number=number)
    assert order.status == Order.Status.PAID
    StockItem.objects.get(product_id=product_id)
    stock = StockItem.objects.get(product_id=product_id)
    assert stock.quantity_on_hand == 3  # 5 - 2
    assert stock.quantity_reserved == 0
    # Confirmation email + recommendation signal fired (eager, post-commit).
    assert len(mail.outbox) == 1
    assert Interaction.objects.filter(event="purchase").count() == 1

    # Order shows up in history.
    history = api_client.get("/api/v1/orders/")
    assert history.data["count"] == 1


def test_oversell_is_prevented(api_client):
    from apps.vendors.models import Vendor
    from tests.conftest import CategoryFactory

    _register_and_login(api_client, "v2@example.com", role="vendor")
    api_client.post("/api/v1/vendors/", {"name": "Bee"}, format="json")
    Vendor.objects.filter(owner__email="v2@example.com").update(status=Vendor.Status.APPROVED)
    cat = CategoryFactory()
    pid = api_client.post(
        "/api/v1/catalog/products/",
        {
            "sku": "lim-1",
            "name": "Limited",
            "price": "10.00",
            "category_id": cat.id,
            "status": "active",
        },
        format="json",
    ).data["id"]
    StockItem.objects.filter(product_id=pid).update(quantity_on_hand=1)

    api_client.credentials()
    _register_and_login(api_client, "buyer2@example.com")
    # Adding more than stock is blocked at the cart boundary.
    resp = api_client.post("/api/v1/cart/items/", {"product_id": pid, "quantity": 5}, format="json")
    assert resp.status_code == 409
    assert resp.data["error"]["code"] == "INVENTORY_INSUFFICIENT"
