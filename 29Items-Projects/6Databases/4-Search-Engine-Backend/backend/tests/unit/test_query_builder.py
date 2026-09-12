"""Query builder is pure — test it exhaustively (it defines relevance behavior)."""

import pytest

from app.core.exceptions import InvalidSearchQueryError
from app.schemas.search import SortOption
from app.search.query_builder import MAX_RESULT_WINDOW, SearchQuery, build_search_body


def test_text_query_and_pagination() -> None:
    body = build_search_body(SearchQuery(query="wireless headphones", page=3, size=20))

    must = body["query"]["bool"]["must"]
    assert must[0]["multi_match"]["query"] == "wireless headphones"
    assert body["from"] == 40
    assert body["size"] == 20
    assert body["track_total_hits"] is True


def test_base_filter_scores_nothing_but_active() -> None:
    body = build_search_body(SearchQuery(query="tv"))
    assert body["query"]["bool"]["filter"] == [{"term": {"is_active": True}}]


def test_facet_selections_go_to_post_filter_not_query() -> None:
    body = build_search_body(
        SearchQuery(query="tv", filters={"brand": ["sony", "lg"], "category": ["tvs"]})
    )

    post = body["post_filter"]["bool"]["filter"]
    assert {"terms": {"brand": ["sony", "lg"]}} in post
    assert {"terms": {"category_slug": ["tvs"]}} in post
    # Selections must NOT narrow the main query (aggs need the full result set).
    assert body["query"]["bool"]["filter"] == [{"term": {"is_active": True}}]


def test_disjunctive_aggs_exclude_own_selection() -> None:
    body = build_search_body(
        SearchQuery(query="tv", filters={"brand": ["sony"], "category": ["tvs"]})
    )

    brand_agg_filters = body["aggs"]["brand"]["filter"]["bool"]["filter"]
    assert {"terms": {"category_slug": ["tvs"]}} in brand_agg_filters
    assert {"terms": {"brand": ["sony"]}} not in brand_agg_filters

    category_agg_filters = body["aggs"]["category"]["filter"]["bool"]["filter"]
    assert {"terms": {"brand": ["sony"]}} in category_agg_filters
    assert {"terms": {"category_slug": ["tvs"]}} not in category_agg_filters


def test_price_range_is_a_selection_clause() -> None:
    body = build_search_body(SearchQuery(query="tv", price_min=100, price_max=500))
    assert {"range": {"price": {"gte": 100, "lte": 500}}} in body["post_filter"]["bool"]["filter"]
    # Term facets see the price refinement; the price facet itself does not.
    assert {"range": {"price": {"gte": 100, "lte": 500}}} in body["aggs"]["brand"]["filter"][
        "bool"
    ]["filter"]
    assert body["aggs"]["price"]["filter"]["bool"]["filter"] == []


def test_no_selections_means_no_post_filter() -> None:
    body = build_search_body(SearchQuery(query="tv"))
    assert "post_filter" not in body
    assert set(body["aggs"]) == {"category", "brand", "price"}


def test_sort_mapping() -> None:
    body = build_search_body(SearchQuery(query="tv", sort=SortOption.price_asc))
    assert body["sort"][0] == {"price": "asc"}


def test_ltr_rescore_present_only_when_enabled() -> None:
    plain = build_search_body(SearchQuery(query="tv"))
    assert "rescore" not in plain

    with_ltr = build_search_body(
        SearchQuery(query="tv"), ltr_mode="plugin", ltr_model_name="m1", ltr_rescore_window=50
    )
    assert with_ltr["rescore"]["window_size"] == 50
    assert with_ltr["rescore"]["query"]["rescore_query"]["sltr"]["model"] == "m1"


def test_ltr_skipped_for_non_relevance_sort() -> None:
    body = build_search_body(
        SearchQuery(query="tv", sort=SortOption.newest), ltr_mode="native", ltr_model_name="m1"
    )
    assert "rescore" not in body


def test_deep_pagination_rejected() -> None:
    with pytest.raises(InvalidSearchQueryError):
        build_search_body(SearchQuery(query="tv", page=MAX_RESULT_WINDOW // 20 + 2, size=20))
