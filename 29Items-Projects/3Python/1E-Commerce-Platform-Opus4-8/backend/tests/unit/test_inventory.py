"""Inventory reservation/commit/release tests."""

from datetime import timedelta

import pytest
from django.utils import timezone

from apps.inventory import services
from apps.inventory.models import Reservation
from apps.inventory.tasks import release_expired_reservations
from common.utils.exceptions import InventoryInsufficientError

pytestmark = pytest.mark.django_db


def test_reserve_decrements_available(stock_factory):
    stock = stock_factory(quantity_on_hand=5)
    services.reserve(cart_id=1, items=[(stock.product_id, 3)])
    stock.refresh_from_db()
    assert stock.quantity_reserved == 3
    assert stock.quantity_available == 2


def test_reserve_insufficient_raises_and_rolls_back(stock_factory):
    stock = stock_factory(quantity_on_hand=2)
    with pytest.raises(InventoryInsufficientError):
        services.reserve(cart_id=1, items=[(stock.product_id, 5)])
    stock.refresh_from_db()
    assert stock.quantity_reserved == 0  # rolled back


def test_commit_reservations_decrements_on_hand(stock_factory):
    stock = stock_factory(quantity_on_hand=5)
    services.reserve(cart_id=7, items=[(stock.product_id, 2)])
    services.commit_reservations(cart_id=7)
    stock.refresh_from_db()
    assert stock.quantity_on_hand == 3
    assert stock.quantity_reserved == 0
    assert not Reservation.objects.filter(cart_id=7).exists()


def test_release_expired_reservations(stock_factory):
    stock = stock_factory(quantity_on_hand=5, quantity_reserved=2)
    Reservation.objects.create(
        stock_item=stock,
        cart_id=9,
        quantity=2,
        expires_at=timezone.now() - timedelta(minutes=1),
    )
    released = release_expired_reservations()
    assert released == 1
    stock.refresh_from_db()
    assert stock.quantity_reserved == 0


def test_release_keeps_unexpired(stock_factory):
    stock = stock_factory(quantity_on_hand=5, quantity_reserved=2)
    Reservation.objects.create(
        stock_item=stock,
        cart_id=9,
        quantity=2,
        expires_at=timezone.now() + timedelta(minutes=10),
    )
    assert release_expired_reservations() == 0
