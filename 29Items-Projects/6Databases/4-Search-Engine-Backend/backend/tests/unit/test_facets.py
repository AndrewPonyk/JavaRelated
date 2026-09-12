"""Facet registry: clause building and response parsing."""

from app.search.facets import (
    build_aggregations,
    build_post_filter,
    parse_facets,
    selection_clauses,
)


def test_selection_clauses_named_per_facet() -> None:
    clauses = selection_clauses({"brand": ["sony"]}, price_min=10, price_max=None)
    assert clauses["brand"] == {"terms": {"brand": ["sony"]}}
    assert clauses["price"] == {"range": {"price": {"gte": 10}}}
    assert "category" not in clauses


def test_post_filter_none_when_no_selections() -> None:
    assert build_post_filter({}) is None


def test_aggregations_always_wrapped_uniformly() -> None:
    aggs = build_aggregations({})
    for name in ("category", "brand", "price"):
        assert aggs[name]["filter"] == {"bool": {"filter": []}}
        assert name in aggs[name]["aggs"]


def test_parse_wrapped_aggregations_marks_selected() -> None:
    aggregations = {
        "brand": {
            "doc_count": 5,
            "brand": {
                "buckets": [
                    {"key": "sony", "doc_count": 3},
                    {"key": "lg", "doc_count": 2},
                    {"key": "empty", "doc_count": 0},  # dropped
                ]
            },
        }
    }
    groups = parse_facets(aggregations, selected={"brand": ["lg"]})

    assert len(groups) == 1
    values = {v.value: v for v in groups[0].values}
    assert values["lg"].selected is True
    assert values["sony"].selected is False
    assert "empty" not in values


def test_parse_unwrapped_aggregations_still_works() -> None:
    aggregations = {"category": {"buckets": [{"key": "tvs", "doc_count": 4}]}}
    groups = parse_facets(aggregations)
    assert groups[0].values[0].value == "tvs"
    assert groups[0].values[0].count == 4


def test_parse_skips_missing_and_empty_facets() -> None:
    assert parse_facets({}) == []
    assert parse_facets({"brand": {"doc_count": 0, "brand": {"buckets": []}}}) == []
