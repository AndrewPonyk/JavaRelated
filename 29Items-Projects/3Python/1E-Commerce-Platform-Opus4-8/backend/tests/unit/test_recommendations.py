"""Recommendation API + signal-capture tests (no torch needed)."""

import pytest
from django.core.cache import cache

from apps.recommendations.ml import inference
from apps.recommendations.models import Interaction

pytestmark = pytest.mark.django_db


def test_recommendations_cold_start_fallback(auth_client, product_factory):
    product_factory()
    product_factory()
    resp = auth_client.get("/api/v1/recommendations/")
    assert resp.status_code == 200
    assert resp.data["source"] == "popularity_fallback"
    assert len(resp.data["results"]) == 2


def test_recommendations_uses_cached_model_results(auth_client, user, product_factory):
    p1 = product_factory()
    p2 = product_factory()
    # Seed the per-user cache as the nightly job would.
    cache.set(inference.CACHE_KEY_TEMPLATE.format(user_id=user.id), [p2.id, p1.id])
    resp = auth_client.get("/api/v1/recommendations/")
    assert resp.data["source"] == "model"
    # Order preserved from the cached ranking.
    assert [r["id"] for r in resp.data["results"]] == [p2.id, p1.id]
    cache.clear()


def test_track_interaction(auth_client, product_in_stock):
    resp = auth_client.post(
        "/api/v1/recommendations/track/",
        {"product_id": product_in_stock.id, "event": "view"},
        format="json",
    )
    assert resp.status_code == 201
    assert Interaction.objects.filter(event="view").count() == 1


def test_track_interaction_bad_product(auth_client):
    resp = auth_client.post(
        "/api/v1/recommendations/track/",
        {"product_id": 999999, "event": "view"},
        format="json",
    )
    assert resp.status_code == 400


def test_capture_recommendation_signal_from_order(user, product_in_stock):
    from apps.cart import services as cart_services
    from apps.orders import services as order_services
    from apps.orders.tasks import capture_recommendation_signal

    cart = cart_services.get_or_create_active_cart(user=user)
    cart_services.add_item(cart=cart, product_id=product_in_stock.id, quantity=2)
    order = order_services.place_order(user=user, cart=cart, payment_token="tok_visa")

    capture_recommendation_signal(order.id)
    interaction = Interaction.objects.get(event=Interaction.Event.PURCHASE)
    assert interaction.product_id == product_in_stock.id
    assert interaction.weight == 2.0


def test_get_cached_recommendations_none(user):
    assert inference.get_cached_recommendations(user.id) is None


def test_train_model_skips_without_interactions():
    from apps.recommendations.tasks import train_model

    assert train_model() == "skipped: no interactions"


def test_precompute_for_user_without_model_returns_empty(user):
    # No checkpoint on disk -> graceful empty result (cold-start handled by view).
    inference._MODEL = None
    assert inference.precompute_for_user(user.id) == []
