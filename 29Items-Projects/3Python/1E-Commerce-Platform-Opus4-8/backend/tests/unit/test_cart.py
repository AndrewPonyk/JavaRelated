"""Cart API + service tests."""

import pytest

from apps.cart import services
from apps.catalog.models import Product
from common.utils.exceptions import DomainError

pytestmark = pytest.mark.django_db


def test_get_cart_creates_one(auth_client):
    resp = auth_client.get("/api/v1/cart/")
    assert resp.status_code == 200
    assert resp.data["items"] == []
    assert resp.data["item_count"] == 0


def test_add_item_to_cart(auth_client, product_in_stock):
    resp = auth_client.post(
        "/api/v1/cart/items/",
        {"product_id": product_in_stock.id, "quantity": 2},
        format="json",
    )
    assert resp.status_code == 201
    assert resp.data["item_count"] == 2
    assert resp.data["total"] == "39.98"  # 2 x 19.99


def test_add_item_again_accumulates(auth_client, product_in_stock):
    auth_client.post(
        "/api/v1/cart/items/",
        {"product_id": product_in_stock.id, "quantity": 1},
        format="json",
    )
    resp = auth_client.post(
        "/api/v1/cart/items/",
        {"product_id": product_in_stock.id, "quantity": 2},
        format="json",
    )
    assert resp.data["item_count"] == 3


def test_add_more_than_stock_rejected(auth_client, product_in_stock):
    resp = auth_client.post(
        "/api/v1/cart/items/",
        {"product_id": product_in_stock.id, "quantity": 999},
        format="json",
    )
    assert resp.status_code == 409
    assert resp.data["error"]["code"] == "INVENTORY_INSUFFICIENT"


def test_add_non_purchasable_rejected(auth_client, product_factory, stock_factory):
    product = product_factory(status=Product.Status.DRAFT)
    stock_factory(product=product, quantity_on_hand=5)
    resp = auth_client.post(
        "/api/v1/cart/items/",
        {"product_id": product.id, "quantity": 1},
        format="json",
    )
    assert resp.status_code == 422
    assert resp.data["error"]["code"] == "PRODUCT_NOT_PURCHASABLE"


def test_update_and_remove_item(auth_client, user, product_in_stock):
    cart = services.get_or_create_active_cart(user=user)
    item = services.add_item(cart=cart, product_id=product_in_stock.id, quantity=1)

    upd = auth_client.patch(f"/api/v1/cart/items/{item.id}/", {"quantity": 3}, format="json")
    assert upd.status_code == 200
    assert upd.data["item_count"] == 3

    rm = auth_client.delete(f"/api/v1/cart/items/{item.id}/")
    assert rm.status_code == 200
    assert rm.data["item_count"] == 0


def test_clear_cart(auth_client, user, product_in_stock):
    cart = services.get_or_create_active_cart(user=user)
    services.add_item(cart=cart, product_id=product_in_stock.id, quantity=2)
    resp = auth_client.delete("/api/v1/cart/")
    assert resp.status_code == 200
    assert resp.data["item_count"] == 0


def test_add_zero_quantity_service_error(user, product_in_stock):
    cart = services.get_or_create_active_cart(user=user)
    with pytest.raises(DomainError):
        services.add_item(cart=cart, product_id=product_in_stock.id, quantity=0)
