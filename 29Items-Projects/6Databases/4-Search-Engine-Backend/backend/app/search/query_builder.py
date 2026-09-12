"""Translates a typed search request into an Elasticsearch query body.

This module is pure (no I/O) and is the ONLY place query DSL is assembled.
User input enters exclusively as typed values — never interpolated into DSL strings —
which structurally prevents query-DSL injection.

Ranking model:
  phase 1: BM25 over boosted fields (+ rank_feature popularity nudge)
  phase 2: optional LTR rescore of the top-`ltr_rescore_window` hits (app/search/ltr.py)

Faceting model (disjunctive, see app/search/facets.py): user refinements go into
`post_filter` (narrow hits, not aggs); each facet's agg excludes only its own clause.

Synonyms are applied by the index's *search-time* analyzer (synonym_graph over a
synonyms set) — nothing to do here; see elasticsearch/indexes/products.index.json.
"""

from dataclasses import dataclass, field
from typing import Any

from app.core.exceptions import InvalidSearchQueryError
from app.schemas.search import SortOption
from app.search.facets import build_aggregations, build_post_filter, selection_clauses
from app.search.ltr import build_ltr_rescore

# Boost weights: exact name matches dominate, brand helps, description is a weak signal.
SEARCH_FIELDS = ["name^3", "brand.text^2", "description", "category_path"]

# ES hard limit for from+size window; deeper access needs search_after.
MAX_RESULT_WINDOW = 10_000

_SORT_CLAUSES: dict[SortOption, list[Any]] = {
    SortOption.relevance: ["_score"],
    SortOption.price_asc: [{"price": "asc"}, "_score"],
    SortOption.price_desc: [{"price": "desc"}, "_score"],
    SortOption.newest: [{"created_at": "desc"}, "_score"],
}


@dataclass(frozen=True)
class SearchQuery:
    """Validated, transport-agnostic search request (built by the endpoint layer)."""

    query: str
    filters: dict[str, list[str]] = field(default_factory=dict)  # facet name -> values
    price_min: float | None = None
    price_max: float | None = None
    sort: SortOption = SortOption.relevance
    page: int = 1
    size: int = 20

    @property
    def offset(self) -> int:
        return (self.page - 1) * self.size


def build_search_body(
    request: SearchQuery,
    *,
    ltr_mode: str = "off",
    ltr_model_name: str = "",
    ltr_rescore_window: int = 100,
) -> dict[str, Any]:
    if request.offset + request.size > MAX_RESULT_WINDOW:
        raise InvalidSearchQueryError(f"Cannot page beyond the first {MAX_RESULT_WINDOW} results.")

    clauses = selection_clauses(request.filters, request.price_min, request.price_max)

    body: dict[str, Any] = {
        "query": {
            "bool": {
                "must": [_text_query(request.query)],
                # Base constraints score nothing and apply to aggs too.
                "filter": [{"term": {"is_active": True}}],
                # Popularity nudges ranking but must not overwhelm text relevance.
                "should": [
                    {
                        "rank_feature": {
                            "field": "popularity",
                            "boost": 0.5,
                            "log": {"scaling_factor": 4},
                        }
                    }
                ],
            }
        },
        "aggs": build_aggregations(clauses),
        "from": request.offset,
        "size": request.size,
        "track_total_hits": True,
        "sort": _SORT_CLAUSES[request.sort],
    }

    post_filter = build_post_filter(clauses)
    if post_filter is not None:
        body["post_filter"] = post_filter

    # LTR only makes sense for relevance-ordered, non-empty text queries.
    if request.query.strip() and request.sort is SortOption.relevance:
        rescore = build_ltr_rescore(
            mode=ltr_mode,
            model_name=ltr_model_name,
            query_text=request.query,
            window_size=ltr_rescore_window,
        )
        if rescore is not None:
            body["rescore"] = rescore

    return body


def _text_query(query: str) -> dict[str, Any]:
    text = query.strip()
    if not text:
        return {"match_all": {}}
    return {
        "multi_match": {
            "query": text,
            "fields": SEARCH_FIELDS,
            "type": "best_fields",
            "tie_breaker": 0.3,
            # Tolerate one missing term on longer queries instead of returning nothing.
            "minimum_should_match": "2<75%",
        }
    }
