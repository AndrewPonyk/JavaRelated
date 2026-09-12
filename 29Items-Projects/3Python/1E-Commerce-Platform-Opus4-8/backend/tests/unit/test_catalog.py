"""Catalog API + service tests."""

import pytest

from apps.catalog import services
from apps.catalog.models import Product

pytestmark = pytest.mark.django_db


def test_list_products_public_excludes_non_active(api_client, product_factory):
    product_factory(status=Product.Status.ACTIVE)
    product_factory(status=Product.Status.DRAFT)
    resp = api_client.get("/api/v1/catalog/products/")
    assert resp.status_code == 200
    assert resp.data["count"] == 1


def test_list_active_products_service_prefetch(product_factory):
    product_factory(status=Product.Status.ACTIVE)
    qs = services.list_active_products()
    assert qs.count() == 1


def test_retrieve_product_by_slug(api_client, product_factory):
    p = product_factory()
    resp = api_client.get(f"/api/v1/catalog/products/{p.slug}/")
    assert resp.status_code == 200
    assert resp.data["sku"] == p.sku


def test_create_product_requires_auth(api_client, vendor_factory):
    cat_resp = api_client.post("/api/v1/catalog/products/", {}, format="json")
    assert cat_resp.status_code == 401


def test_vendor_can_create_product(api_client, vendor_factory):
    vendor = vendor_factory()
    api_client.force_authenticate(user=vendor.owner)
    # category needed
    from tests.conftest import CategoryFactory

    cat = CategoryFactory()
    resp = api_client.post(
        "/api/v1/catalog/products/",
        {"sku": "abc-1", "name": "Widget", "price": "9.99", "category_id": cat.id},
        format="json",
    )
    assert resp.status_code == 201, resp.data
    product = Product.objects.get(sku="ABC-1")  # sku upper-cased by validator
    assert product.vendor == vendor
    # A stock row is auto-created.
    assert hasattr(product, "stock")


def test_create_product_without_vendor_profile_denied(auth_client):
    from tests.conftest import CategoryFactory

    cat = CategoryFactory()
    resp = auth_client.post(
        "/api/v1/catalog/products/",
        {"sku": "x-1", "name": "X", "price": "1.00", "category_id": cat.id},
        format="json",
    )
    assert resp.status_code == 403


def test_duplicate_sku_rejected(api_client, vendor_factory, product_factory):
    existing = product_factory()
    vendor = vendor_factory()
    api_client.force_authenticate(user=vendor.owner)
    from tests.conftest import CategoryFactory

    cat = CategoryFactory()
    resp = api_client.post(
        "/api/v1/catalog/products/",
        {"sku": existing.sku, "name": "Dup", "price": "5.00", "category_id": cat.id},
        format="json",
    )
    assert resp.status_code == 400
    assert "sku" in resp.data["error"]["details"]


def test_vendor_cannot_edit_others_product(api_client, vendor_factory, product_factory):
    other_product = product_factory()
    intruder = vendor_factory()
    api_client.force_authenticate(user=intruder.owner)
    resp = api_client.patch(
        f"/api/v1/catalog/products/{other_product.slug}/",
        {"name": "Hacked"},
        format="json",
    )
    assert resp.status_code in (403, 404)


def test_negative_price_rejected_by_validator(api_client, vendor_factory):
    vendor = vendor_factory()
    api_client.force_authenticate(user=vendor.owner)
    from tests.conftest import CategoryFactory

    cat = CategoryFactory()
    resp = api_client.post(
        "/api/v1/catalog/products/",
        {"sku": "neg-1", "name": "Neg", "price": "-1.00", "category_id": cat.id},
        format="json",
    )
    assert resp.status_code == 400


def test_archive_product_service(product_factory):
    p = product_factory()
    services.archive_product(p)
    p.refresh_from_db()
    assert p.status == Product.Status.ARCHIVED
