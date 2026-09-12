"""Facet registry: single place that defines which facets exist, how they are
aggregated in ES, and how aggregation responses map back to API DTOs.

Disjunctive (multi-select) faceting: user selections are applied as a `post_filter`
(so they narrow hits but not aggregations), and every facet's aggregation is wrapped
in a `filter` agg containing all selections EXCEPT its own. Result: after picking
Brand=Sony, other brands keep their true counts while categories/prices reflect the
Sony selection. Adding a facet = one FacetDefinition + the field in the index mapping.
"""

from dataclasses import dataclass
from typing import Any

from app.schemas.search import FacetGroup, FacetValue


@dataclass(frozen=True)
class FacetDefinition:
    name: str  # API name (query param + response key)
    label: str  # display label for the UI
    field: str  # ES field (keyword / numeric)
    kind: str = "terms"  # "terms" | "range"
    size: int = 30  # max buckets for terms facets


TERM_FACETS: tuple[FacetDefinition, ...] = (
    FacetDefinition(name="category", label="Category", field="category_slug"),
    FacetDefinition(name="brand", label="Brand", field="brand"),
)

PRICE_FACET = FacetDefinition(name="price", label="Price", field="price", kind="range")

# Display buckets; actual filtering uses the price_min/price_max params.
PRICE_RANGES: list[dict[str, Any]] = [
    {"key": "under-25", "to": 25},
    {"key": "25-50", "from": 25, "to": 50},
    {"key": "50-100", "from": 50, "to": 100},
    {"key": "100-250", "from": 100, "to": 250},
    {"key": "over-250", "from": 250},
]

ALL_FACETS: tuple[FacetDefinition, ...] = (*TERM_FACETS, PRICE_FACET)


def selection_clauses(
    filters: dict[str, list[str]],
    price_min: float | None = None,
    price_max: float | None = None,
) -> dict[str, dict[str, Any]]:
    """User refinements as named filter clauses: facet selections + price range.

    Named so each facet's aggregation can exclude exactly its own clause.
    """
    clauses: dict[str, dict[str, Any]] = {}
    for facet in TERM_FACETS:
        values = filters.get(facet.name)
        if values:
            clauses[facet.name] = {"terms": {facet.field: values}}

    price_range: dict[str, float] = {}
    if price_min is not None:
        price_range["gte"] = price_min
    if price_max is not None:
        price_range["lte"] = price_max
    if price_range:
        clauses[PRICE_FACET.name] = {"range": {"price": price_range}}
    return clauses


def build_post_filter(clauses: dict[str, dict[str, Any]]) -> dict[str, Any] | None:
    if not clauses:
        return None
    return {"bool": {"filter": list(clauses.values())}}


def build_aggregations(clauses: dict[str, dict[str, Any]]) -> dict[str, Any]:
    """Each facet agg is filtered by all OTHER selections (disjunctive counts).

    Aggs are always wrapped in a filter agg (empty bool == match_all) so the
    response shape is uniform for parse_facets.
    """

    def inner_agg(facet: FacetDefinition) -> dict[str, Any]:
        if facet.kind == "range":
            return {"range": {"field": facet.field, "ranges": PRICE_RANGES}}
        return {"terms": {"field": facet.field, "size": facet.size}}

    aggs: dict[str, Any] = {}
    for facet in ALL_FACETS:
        others = [clause for name, clause in clauses.items() if name != facet.name]
        aggs[facet.name] = {
            "filter": {"bool": {"filter": others}},
            "aggs": {facet.name: inner_agg(facet)},
        }
    return aggs


def parse_facets(
    aggregations: dict[str, Any], selected: dict[str, list[str]] | None = None
) -> list[FacetGroup]:
    """ES aggregations response → ordered facet groups with selection state.

    Handles both the wrapped shape produced by build_aggregations and a plain
    unwrapped terms/range agg (defensive for ad-hoc queries).
    """
    selected = selected or {}
    groups: list[FacetGroup] = []
    for facet in ALL_FACETS:
        agg = aggregations.get(facet.name)
        if not agg:
            continue
        if "buckets" not in agg and facet.name in agg:  # unwrap the filter agg
            agg = agg[facet.name]
        chosen = set(selected.get(facet.name, []))
        values = [
            FacetValue(
                value=str(bucket["key"]),
                count=bucket["doc_count"],
                selected=str(bucket["key"]) in chosen,
            )
            for bucket in agg.get("buckets", [])
            if bucket["doc_count"] > 0
        ]
        if values:
            groups.append(FacetGroup(name=facet.name, label=facet.label, values=values))
    return groups
