"""Search API — faceted full-text product search backed by Elasticsearch."""

from __future__ import annotations

import logging

from django.conf import settings
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView

from .client import get_client

logger = logging.getLogger(__name__)


def _es_unavailable() -> Response:
    return Response(
        {"error": {"code": "SEARCH_UNAVAILABLE", "message": "Search is temporarily unavailable."}},
        status=status.HTTP_503_SERVICE_UNAVAILABLE,
    )


class ProductSearchView(APIView):
    """GET /api/v1/search/?q=...&category=...&min_price=...&max_price=..."""

    permission_classes = [AllowAny]

    @extend_schema(
        parameters=[
            OpenApiParameter("q", str, description="Full-text query"),
            OpenApiParameter("category", str),
            OpenApiParameter("vendor", str),
            OpenApiParameter("min_price", float),
            OpenApiParameter("max_price", float),
            OpenApiParameter("page_size", int),
        ],
        responses=OpenApiTypes.OBJECT,
    )
    def get(self, request: Request) -> Response:
        query = request.query_params.get("q", "")
        category = request.query_params.get("category")
        vendor = request.query_params.get("vendor")
        min_price = request.query_params.get("min_price")
        max_price = request.query_params.get("max_price")

        must: list[dict] = []
        if query:
            must.append({"multi_match": {"query": query, "fields": ["name^3", "description"]}})
        else:
            must.append({"match_all": {}})

        filters: list[dict] = [{"term": {"status": "active"}}]
        if category:
            filters.append({"term": {"category": category}})
        if vendor:
            filters.append({"term": {"vendor": vendor}})
        if min_price or max_price:
            rng: dict = {}
            if min_price:
                rng["gte"] = float(min_price)
            if max_price:
                rng["lte"] = float(max_price)
            filters.append({"range": {"price": rng}})

        try:
            page_size = min(int(request.query_params.get("page_size", 24)), 100)
        except ValueError:
            page_size = 24

        body = {
            "query": {"bool": {"must": must, "filter": filters}},
            "aggs": {
                "categories": {"terms": {"field": "category"}},
                "vendors": {"terms": {"field": "vendor"}},
            },
            "size": page_size,
        }

        try:
            result = get_client().search(index=settings.ELASTICSEARCH_PRODUCT_INDEX, body=body)
        except Exception:  # connection / transport errors
            logger.exception("search.query.failed")
            return _es_unavailable()

        hits = [h["_source"] | {"id": h["_id"]} for h in result["hits"]["hits"]]
        return Response(
            {
                "results": hits,
                "total": result["hits"]["total"]["value"],
                "facets": result.get("aggregations", {}),
            }
        )


class AutocompleteView(APIView):
    """GET /api/v1/search/autocomplete/?q=... — edge-ngram name suggestions."""

    permission_classes = [AllowAny]

    @extend_schema(
        parameters=[OpenApiParameter("q", str, description="Prefix to complete")],
        responses=OpenApiTypes.OBJECT,
    )
    def get(self, request: Request) -> Response:
        query = request.query_params.get("q", "").strip()
        if not query:
            return Response({"suggestions": []})

        body = {
            "size": 8,
            "_source": ["name", "sku"],
            "query": {
                "bool": {
                    "must": [{"match": {"name": {"query": query, "operator": "and"}}}],
                    "filter": [{"term": {"status": "active"}}],
                }
            },
        }
        try:
            result = get_client().search(index=settings.ELASTICSEARCH_PRODUCT_INDEX, body=body)
        except Exception:
            logger.exception("search.autocomplete.failed")
            return _es_unavailable()

        suggestions = [h["_source"]["name"] for h in result["hits"]["hits"]]
        return Response({"suggestions": suggestions})
