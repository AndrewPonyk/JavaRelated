"""Search views for E-Commerce Platform."""
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from .services import SearchService


def validate_positive_int(value: str, default: int, max_value: int) -> int:
    """Safely convert and validate a positive integer parameter."""
    try:
        result = int(value)
        if result < 1:
            return default
        return min(result, max_value)
    except (ValueError, TypeError):
        return default


class ProductSearchView(APIView):
    """View for product search."""

    permission_classes = [permissions.AllowAny]
    MAX_PAGE_SIZE = 100
    MAX_PAGE = 1000

    def get(self, request):
        """Search products with filters and facets."""
        query = request.query_params.get('q', '')[:500]  # Limit query length
        category = request.query_params.get('category', '')[:100]
        vendor = request.query_params.get('vendor', '')[:100]
        min_price = request.query_params.get('min_price')
        max_price = request.query_params.get('max_price')
        sort = request.query_params.get('sort', 'relevance')

        # Validate pagination parameters
        page = validate_positive_int(
            request.query_params.get('page', '1'), 1, self.MAX_PAGE
        )
        page_size = validate_positive_int(
            request.query_params.get('page_size', '20'), 20, self.MAX_PAGE_SIZE
        )

        # Validate sort parameter
        valid_sorts = {'relevance', 'price_asc', 'price_desc', 'newest', 'rating'}
        if sort not in valid_sorts:
            sort = 'relevance'

        # Validate price parameters
        try:
            min_price = float(min_price) if min_price else None
            if min_price is not None and min_price < 0:
                min_price = None
        except ValueError:
            min_price = None

        try:
            max_price = float(max_price) if max_price else None
            if max_price is not None and max_price < 0:
                max_price = None
        except ValueError:
            max_price = None

        results = SearchService.search_products(
            query=query,
            category=category or None,
            vendor=vendor or None,
            min_price=min_price,
            max_price=max_price,
            sort=sort,
            page=page,
            page_size=page_size
        )

        return Response(results)


class SearchSuggestView(APIView):
    """View for search suggestions/autocomplete."""

    permission_classes = [permissions.AllowAny]

    def get(self, request):
        """Get search suggestions for a query."""
        query = request.query_params.get('q', '')[:200]  # Limit query length

        if len(query) < 2:
            return Response({'suggestions': []})

        suggestions = SearchService.get_suggestions(query)
        return Response({'suggestions': suggestions})


class TrendingSearchesView(APIView):
    """View for trending/popular searches."""

    permission_classes = [permissions.AllowAny]

    def get(self, request):
        """Get trending searches."""
        # Returns static trending searches
        # Production would query search logs with aggregation
        trending = [
            'electronics',
            'clothing',
            'home decor',
            'fitness',
            'accessories',
        ]
        return Response({'trending': trending})
