"""
Search Controller (ViewSets)

REST API endpoints for product search using Elasticsearch.
"""

from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import AllowAny
from elasticsearch_dsl import Q

from backend.core.models import Product
from backend.api.serializers import ProductSerializer
from backend.api.services.search_service import SearchService


class SearchViewSet(viewsets.ViewSet):
    """
    ViewSet for search operations using Elasticsearch.
    """

    permission_classes = [AllowAny]
    search_service = SearchService()

    @action(detail=False, methods=["get"])
    def products(self, request):
        """
        Search products using Elasticsearch.

        Query parameters:
        - q: Search query string
        - category: Filter by category ID
        - min_price: Minimum price filter
        - max_price: Maximum price filter
        - vendor: Filter by vendor ID
        - in_stock: Only show items in stock
        - page: Page number (default: 1)
        - page_size: Results per page (default: 20)
        """
        query = request.query_params.get("q", "")
        filters = {
            "category": request.query_params.get("category"),
            "min_price": request.query_params.get("min_price"),
            "max_price": request.query_params.get("max_price"),
            "vendor": request.query_params.get("vendor"),
            "in_stock": request.query_params.get("in_stock") == "true",
        }
        page = int(request.query_params.get("page", 1))
        page_size = int(request.query_params.get("page_size", 20))

        try:
            results = self.search_service.search_products(
                query=query,
                filters=filters,
                page=page,
                page_size=page_size,
            )

            # Fetch full product details
            product_ids = [hit["id"] for hit in results["hits"]]
            products = Product.objects.filter(id__in=product_ids)
            products_dict = {p.id: p for p in products}

            # Order products by search relevance
            ordered_products = [
                products_dict.get(hit["id"]) for hit in results["hits"] if products_dict.get(hit["id"])
            ]

            serializer = ProductSerializer(ordered_products, many=True)

            return Response(
                {
                    "results": serializer.data,
                    "total": results["total"],
                    "page": page,
                    "page_size": page_size,
                    "total_pages": (results["total"] + page_size - 1) // page_size,
                }
            )

        except Exception as e:
            # Fallback to database search if Elasticsearch fails
            return self._database_search(query, filters, page, page_size)

    @action(detail=False, methods=["get"])
    def autocomplete(self, request):
        """
        Get autocomplete suggestions for product search.
        """
        query = request.query_params.get("q", "")
        if len(query) < 2:
            return Response({"suggestions": []})

        try:
            suggestions = self.search_service.get_autocomplete_suggestions(query)
            return Response({"suggestions": suggestions})
        except Exception:
            return Response({"suggestions": []})

    @action(detail=False, methods=["get"])
    def aggregations(self, request):
        """
        Get search aggregations (facets) for filtering.
        """
        query = request.query_params.get("q", "")
        category = request.query_params.get("category")

        try:
            aggregations = self.search_service.get_search_aggregations(
                query=query,
                category=category,
            )
            return Response(aggregations)
        except Exception:
            return Response({
                "categories": [],
                "vendors": [],
                "price_range": {"min": 0, "max": 1000},
            })

    def _database_search(self, query, filters, page, page_size):
        """Fallback database search when Elasticsearch is unavailable."""
        from django.db.models import Q

        queryset = Product.objects.filter(is_active=True)

        # Search query
        if query:
            queryset = queryset.filter(
                Q(name__icontains=query) |
                Q(description__icontains=query) |
                Q(short_description__icontains=query)
            )

        # Apply filters
        if filters.get("category"):
            queryset = queryset.filter(category_id=filters["category"])
        if filters.get("vendor"):
            queryset = queryset.filter(vendor_id=filters["vendor"])
        if filters.get("min_price"):
            queryset = queryset.filter(price__gte=filters["min_price"])
        if filters.get("max_price"):
            queryset = queryset.filter(price__lte=filters["max_price"])
        if filters.get("in_stock"):
            queryset = queryset.filter(stock__gt=0)

        # Pagination
        start = (page - 1) * page_size
        end = start + page_size
        total = queryset.count()
        products = queryset[start:end]

        serializer = ProductSerializer(products, many=True)

        return Response(
            {
                "results": serializer.data,
                "total": total,
                "page": page,
                "page_size": page_size,
                "total_pages": (total + page_size - 1) // page_size,
            }
        )
