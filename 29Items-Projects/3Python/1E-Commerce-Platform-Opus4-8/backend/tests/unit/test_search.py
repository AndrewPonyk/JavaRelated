"""Search API tests — Elasticsearch client is mocked (no ES server needed)."""

from unittest.mock import MagicMock

import pytest

pytestmark = pytest.mark.django_db

_ES_RESPONSE = {
    "hits": {
        "total": {"value": 1},
        "hits": [{"_id": "1", "_source": {"name": "Red Shoe", "price": 9.99, "category": "shoes"}}],
    },
    "aggregations": {"categories": {"buckets": [{"key": "shoes", "doc_count": 1}]}},
}


def test_search_returns_results_and_facets(api_client, mocker):
    client = MagicMock()
    client.search.return_value = _ES_RESPONSE
    mocker.patch("apps.search.views.get_client", return_value=client)

    resp = api_client.get("/api/v1/search/?q=shoe&category=shoes&min_price=5&max_price=20")
    assert resp.status_code == 200
    assert resp.data["total"] == 1
    assert resp.data["results"][0]["name"] == "Red Shoe"
    assert "categories" in resp.data["facets"]
    # Verify the price range filter was applied to the ES query body.
    body = client.search.call_args.kwargs["body"]
    assert {"range": {"price": {"gte": 5.0, "lte": 20.0}}} in body["query"]["bool"]["filter"]


def test_search_handles_es_down(api_client, mocker):
    mocker.patch("apps.search.views.get_client", side_effect=Exception("conn refused"))
    resp = api_client.get("/api/v1/search/?q=shoe")
    assert resp.status_code == 503
    assert resp.data["error"]["code"] == "SEARCH_UNAVAILABLE"


def test_autocomplete(api_client, mocker):
    client = MagicMock()
    client.search.return_value = {
        "hits": {"hits": [{"_source": {"name": "Red Shoe", "sku": "RS1"}}]}
    }
    mocker.patch("apps.search.views.get_client", return_value=client)
    resp = api_client.get("/api/v1/search/autocomplete/?q=red")
    assert resp.status_code == 200
    assert resp.data["suggestions"] == ["Red Shoe"]


def test_autocomplete_empty_query(api_client):
    resp = api_client.get("/api/v1/search/autocomplete/?q=")
    assert resp.status_code == 200
    assert resp.data["suggestions"] == []


def test_index_product_task_builds_document(product_factory, mocker):
    """The indexing task should push the right document shape to ES."""
    from apps.search import tasks

    product = product_factory()
    client = MagicMock()
    mocker.patch("apps.search.client.get_client", return_value=client)
    tasks.index_product(product.id)
    assert client.index.called
    doc = client.index.call_args.kwargs["document"]
    assert doc["sku"] == product.sku
    assert doc["status"] == "active"


def test_index_missing_product_is_noop(mocker):
    from apps.search import tasks

    client = MagicMock()
    mocker.patch("apps.search.client.get_client", return_value=client)
    tasks.index_product(999999)
    assert not client.index.called


def test_remove_product_task(mocker):
    from apps.search import tasks

    client = MagicMock()
    mocker.patch("apps.search.client.get_client", return_value=client)
    tasks.remove_product(5)
    # ES 8.x: delete is issued via client.options(ignore_status=404).delete(...)
    client.options.assert_called_once_with(ignore_status=404)
    client.options.return_value.delete.assert_called_once()


def test_full_reindex_enqueues_each_active_product(product_factory, mocker):
    from apps.search import tasks

    product_factory()
    product_factory()
    mocker.patch("apps.search.client.ensure_index")
    delay = mocker.patch("apps.search.tasks.index_product.delay")
    count = tasks.full_reindex()
    assert count == 2
    assert delay.call_count == 2
