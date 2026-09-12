"""Shared pytest fixtures + factory_boy factories."""

from __future__ import annotations

from decimal import Decimal

import factory
import pytest
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient

from apps.catalog.models import Category, Product
from apps.inventory.models import StockItem
from apps.vendors.models import Vendor

User = get_user_model()


# --------------------------------------------------------------------------
# Factories
# --------------------------------------------------------------------------
class UserFactory(factory.django.DjangoModelFactory):
    class Meta:
        model = User

    email = factory.Sequence(lambda n: f"user{n}@example.com")
    username = factory.LazyAttribute(lambda o: o.email)
    role = User.Role.CUSTOMER

    @classmethod
    def _create(cls, model_class, *args, **kwargs):
        password = kwargs.pop("password", "pw-test-1234")
        user = model_class.objects.create_user(password=password, **kwargs)
        return user


class VendorFactory(factory.django.DjangoModelFactory):
    class Meta:
        model = Vendor

    owner = factory.SubFactory(UserFactory, role=User.Role.VENDOR)
    name = factory.Sequence(lambda n: f"Vendor {n}")
    status = Vendor.Status.APPROVED


class CategoryFactory(factory.django.DjangoModelFactory):
    class Meta:
        model = Category

    name = factory.Sequence(lambda n: f"Category {n}")


class ProductFactory(factory.django.DjangoModelFactory):
    class Meta:
        model = Product

    sku = factory.Sequence(lambda n: f"SKU-{n:05d}")
    name = factory.Sequence(lambda n: f"Product {n}")
    description = "A fine product."
    price = Decimal("19.99")
    status = Product.Status.ACTIVE
    category = factory.SubFactory(CategoryFactory)
    vendor = factory.SubFactory(VendorFactory)


class StockItemFactory(factory.django.DjangoModelFactory):
    class Meta:
        model = StockItem

    product = factory.SubFactory(ProductFactory)
    quantity_on_hand = 10
    quantity_reserved = 0


# --------------------------------------------------------------------------
# Fixtures
# --------------------------------------------------------------------------
@pytest.fixture
def user_factory():
    return UserFactory


@pytest.fixture
def vendor_factory():
    return VendorFactory


@pytest.fixture
def product_factory():
    return ProductFactory


@pytest.fixture
def stock_factory():
    return StockItemFactory


@pytest.fixture
def api_client() -> APIClient:
    return APIClient()


@pytest.fixture
def user(db):
    return UserFactory()


@pytest.fixture
def auth_client(api_client, user):
    api_client.force_authenticate(user=user)
    return api_client


@pytest.fixture
def product_in_stock(db):
    """An active product with 10 units on hand."""
    stock = StockItemFactory()
    return stock.product
