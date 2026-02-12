"""
Search Service

Business logic for Elasticsearch search operations.
"""

from typing import List, Dict, Any, Optional
from elasticsearch_dsl import Search, Q

from backend.core.config.settings import settings


class SearchService:
    """Service class for Elasticsearch search operations."""

    def __init__(self):
        self.index_prefix = settings.elasticsearch_index_prefix
        self.product_index = f"{self.index_prefix}_products"

    def search_products(
        self,
        query: str = "",
        filters: Dict[str, Any] = None,
        page: int = 1,
        page_size: int = 20,
        sort: str = "relevance",
    ) -> Dict[str, Any]:
        """
        Search products using Elasticsearch.

        Args:
            query: Search query string
            filters: Dictionary of filters (category, price range, etc.)
            page: Page number
            page_size: Results per page
            sort: Sort method (relevance, price_asc, price_desc, rating, newest)

        Returns:
            Dictionary with search results and metadata
        """
        filters = filters or {}
        search = Search(index=self.product_index)

        # Build query
        if query:
            # Multi-match query across multiple fields
            search = search.query(
                "multi_match",
                query=query,
                fields=[
                    "name^3",  # Boost name matches
                    "short_description^2",
                    "description",
                    "category_name^2",
                ],
                fuzziness="AUTO",
            )
        else:
            # Match all if no query
            search = search.query("match_all")

        # Apply filters
        if filters.get("category"):
            search = search.filter("term", category_id=int(filters["category"]))

        if filters.get("vendor"):
            search = search.filter("term", vendor_id=int(filters["vendor"]))

        if filters.get("min_price") or filters.get("max_price"):
            range_filter = {}
            if filters.get("min_price"):
                range_filter["gte"] = float(filters["min_price"])
            if filters.get("max_price"):
                range_filter["lte"] = float(filters["max_price"])
            search = search.filter("range", price=range_filter)

        if filters.get("in_stock"):
            search = search.filter("range", stock={"gt": 0})

        # Apply sorting
        sort_mapping = {
            "relevance": ["_score", "-created_at"],
            "price_asc": ["price", "-created_at"],
            "price_desc": ["-price", "-created_at"],
            "rating": ["-average_rating", "-review_count"],
            "newest": ["-created_at"],
            "popular": ["-review_count", "-average_rating"],
        }
        search = search.sort(*sort_mapping.get(sort, sort_mapping["relevance"]))

        # Apply pagination
        start = (page - 1) * page_size
        search = search[start : start + page_size]

        # Execute search
        try:
            response = search.execute()
            return {
                "hits": [
                    {
                        "id": hit.meta.id,
                        "score": hit.meta.score,
                        **hit.to_dict(),
                    }
                    for hit in response.hits
                ],
                "total": response.hits.total.value,
                "page": page,
                "page_size": page_size,
            }
        except Exception as e:
            # Log error and return empty results
            import logging
            logger = logging.getLogger("backend")
            logger.warning(f"Elasticsearch error: {e}")
            return {
                "hits": [],
                "total": 0,
                "page": page,
                "page_size": page_size,
            }

    def get_autocomplete_suggestions(
        self,
        query: str,
        limit: int = 10,
    ) -> List[Dict[str, Any]]:
        """
        Get autocomplete suggestions for search query.

        Args:
            query: Search query string
            limit: Maximum number of suggestions

        Returns:
            List of suggestion dictionaries
        """
        search = Search(index=self.product_index)

        # Use completion suggester or prefix query
        search = search.query(
            "prefix",
            name={"value": query, "boost": 2.0},
        )
        search = search.query(
            "prefix",
            short_description={"value": query},
        )

        search = search[:limit]
        search = search.source(["name", "slug", "main_image"])

        try:
            response = search.execute()
            return [
                {
                    "name": hit.name,
                    "slug": hit.slug,
                    "image": hit.main_image if hasattr(hit, "main_image") else None,
                }
                for hit in response.hits
            ]
        except Exception:
            return []

    def get_search_aggregations(
        self,
        query: str = "",
        category: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Get aggregations (facets) for search results.

        Args:
            query: Search query string
            category: Filter by category

        Returns:
            Dictionary with aggregation results
        """
        search = Search(index=self.product_index)

        # Build base query
        if query:
            search = search.query("multi_match", query=query, fields=["name", "description"])
        else:
            search = search.query("match_all")

        if category:
            search = search.filter("term", category_id=int(category))

        # Add aggregations
        search.aggs.bucket("categories", "terms", field="category_name", size=20)
        search.aggs.bucket("vendors", "terms", field="vendor_name", size=20)
        search.aggs.bucket(
            "price_range",
            "range",
            field="price",
            ranges=[
                {"to": 25, "key": "0-25"},
                {"from": 25, "to": 50, "key": "25-50"},
                {"from": 50, "to": 100, "key": "50-100"},
                {"from": 100, "to": 200, "key": "100-200"},
                {"from": 200, "key": "200+"},
            ],
        )

        try:
            response = search.execute()

            return {
                "categories": [
                    {"name": bucket.key, "count": bucket.doc_count}
                    for bucket in response.aggregations.categories.buckets
                ],
                "vendors": [
                    {"name": bucket.key, "count": bucket.doc_count}
                    for bucket in response.aggregations.vendors.buckets
                ],
                "price_range": {
                    "0-25": 0,
                    "25-50": 0,
                    "50-100": 0,
                    "100-200": 0,
                    "200+": 0,
                },
            }
        except Exception:
            return {
                "categories": [],
                "vendors": [],
                "price_range": {
                    "0-25": 0,
                    "25-50": 0,
                    "50-100": 0,
                    "100-200": 0,
                    "200+": 0,
                },
            }

    def index_product(self, product_data: Dict[str, Any]) -> bool:
        """
        Index a product in Elasticsearch.

        Args:
            product_data: Product data to index

        Returns:
            True if successful, False otherwise
        """
        try:
            from elasticsearch import Elasticsearch

            es = Elasticsearch([settings.elasticsearch_url])

            doc = {
                "id": product_data["id"],
                "name": product_data["name"],
                "slug": product_data["slug"],
                "short_description": product_data.get("short_description", ""),
                "description": product_data.get("description", ""),
                "price": float(product_data["price"]),
                "category_id": product_data.get("category"),
                "category_name": product_data.get("category_name", ""),
                "vendor_id": product_data.get("vendor"),
                "vendor_name": product_data.get("vendor_name", ""),
                "stock": product_data.get("stock", 0),
                "average_rating": float(product_data.get("average_rating", 0)),
                "review_count": product_data.get("review_count", 0),
                "is_active": product_data.get("is_active", True),
                "created_at": product_data.get("created_at"),
            }

            es.index(index=self.product_index, id=product_data["id"], body=doc)
            return True
        except Exception as e:
            import logging
            logger = logging.getLogger("backend")
            logger.error(f"Elasticsearch index error: {e}")
            return False

    def delete_product(self, product_id: int) -> bool:
        """
        Delete a product from Elasticsearch index.

        Args:
            product_id: The product ID

        Returns:
            True if successful, False otherwise
        """
        try:
            from elasticsearch import Elasticsearch

            es = Elasticsearch([settings.elasticsearch_url])
            es.delete(index=self.product_index, id=product_id)
            return True
        except Exception:
            return False


class SearchAnalytics:
    """Service for tracking and analyzing search behavior."""

    @staticmethod
    def track_search(user_id: int, query: str, results_count: int,
                     filters: dict = None) -> None:
        """
        Track a search query for analytics.

        Args:
            user_id: The user ID (None for anonymous)
            query: Search query string
            results_count: Number of results returned
            filters: Applied filters
        """
        from backend.core.models import SearchQuery

        SearchQuery.objects.create(
            user_id=user_id,
            query=query.lower(),
            results_count=results_count,
            filters=filters or {},
        )

    @staticmethod
    def get_trending_queries(limit: int = 10) -> list:
        """
        Get most popular search queries.

        Args:
            limit: Maximum number of queries to return

        Returns:
            List of (query, count) tuples
        """
        from backend.core.models import SearchQuery
        from django.utils import timezone
        from datetime import timedelta

        # Get searches from last 7 days
        since = timezone.now() - timedelta(days=7)

        trending = SearchQuery.objects.filter(
            created_at__gte=since
        ).values("query").annotate(
            count=Count("id")
        ).order_by("-count")[:limit]

        return [(t["query"], t["count"]) for t in trending]

    @staticmethod
    def get_user_search_history(user_id: int, limit: int = 20) -> list:
        """
        Get search history for a user.

        Args:
            user_id: The user ID
            limit: Maximum number of queries to return

        Returns:
            List of search queries
        """
        from backend.core.models import SearchQuery

        queries = SearchQuery.objects.filter(
            user_id=user_id
        ).values("query", "created_at").order_by("-created_at")[:limit]

        return list(queries)


class FuzzySearchService:
    """Service for fuzzy search functionality."""

    def __init__(self):
        self.max_distance = 2  # Maximum Levenshtein distance

    def fuzzy_search(self, query: str, max_results: int = 10) -> dict:
        """
        Perform fuzzy search on products.

        Args:
            query: Search query
            max_results: Maximum number of results

        Returns:
            Dictionary with matches and scores
        """
        from backend.core.models import Product

        # First try exact match
        exact_matches = Product.objects.filter(
            Q(name__icontains=query) |
            Q(description__icontains=query) |
            Q(short_description__icontains=query),
            is_active=True
        )[:max_results]

        if exact_matches.count() >= max_results:
            return self._format_results(exact_matches, 1.0)

        # If not enough results, try fuzzy matching
        all_products = Product.objects.filter(is_active=True)
        fuzzy_results = []

        for product in all_products:
            name_score = self._levenshtein_similarity(query.lower(), product.name.lower())
            desc_score = self._levenshtein_similarity(query.lower(), product.description.lower())
            score = max(name_score, desc_score)

            if score > 0.6:  # Minimum similarity threshold
                fuzzy_results.append((product, score))

        # Sort by score and take top results
        fuzzy_results.sort(key=lambda x: x[1], reverse=True)
        matched_products = [r[0] for r in fuzzy_results[:max_results]]

        return self._format_results(matched_products, fuzzy_results)

    def get_did_you_mean(self, query: str, limit: int = 3) -> list:
        """
        Get "did you mean" suggestions for misspelled queries.

        Args:
            query: Search query
            limit: Maximum number of suggestions

        Returns:
            List of suggested queries
        """
        from backend.core.models import Product
        from collections import Counter

        # Extract words from product names
        words = []
        for product in Product.objects.filter(is_active=True):
            words.extend(product.name.lower().split())

        # Count word frequency
        word_freq = Counter(words)

        # Find similar words for each word in query
        query_words = query.lower().split()
        suggestions = []

        for query_word in query_words:
            similar_words = []
            for word, freq in word_freq.items():
                similarity = self._levenshtein_similarity(query_word, word)
                if similarity > 0.7:
                    similar_words.append((word, similarity * freq))

            similar_words.sort(key=lambda x: x[1], reverse=True)
            if similar_words:
                suggestions.append(similar_words[0][0])

        # Generate combinations
        if suggestions:
            # Simple suggestion: replace misspelled words
            corrected = " ".join(suggestions)
            return [corrected]

        return []

    def _levenshtein_similarity(self, s1: str, s2: str) -> float:
        """
        Calculate similarity ratio between two strings.

        Returns a value between 0 and 1.
        """
        if not s1 or not s2:
            return 0.0

        # Fast path for exact match
        if s1 == s2:
            return 1.0

        # Simple starts-with or contains check
        if s1 in s2 or s2 in s1:
            return 0.8

        # Calculate Levenshtein distance (simplified)
        len1, len2 = len(s1), len(s2)
        if abs(len1 - len2) > self.max_distance:
            return 0.0

        # Use a simple similarity metric
        from difflib import SequenceMatcher
        return SequenceMatcher(None, s1, s2).ratio()

    def _format_results(self, products, scores) -> dict:
        """Format search results."""
        if isinstance(scores, list):
            return {
                "results": [
                    {
                        "id": p.id,
                        "name": p.name,
                        "slug": p.slug,
                        "price": float(p.price),
                        "image": p.main_image.url if p.main_image else None,
                        "score": score,
                    }
                    for p, score in scores
                ]
            }

        return {
            "results": [
                {
                    "id": p.id,
                    "name": p.name,
                    "slug": p.slug,
                    "price": float(p.price),
                    "image": p.main_image.url if p.main_image else None,
                }
                for p in products
            ]
        }


def get_collaborative_recommendations(user_id: int, limit: int = 10) -> list:
    """
    Get product recommendations using collaborative filtering.

    Args:
        user_id: The user ID
        limit: Maximum number of recommendations

    Returns:
        List of recommended product IDs
    """
    from backend.core.models import OrderItem, Order
    from collections import defaultdict

    # Get products purchased by user
    user_products = set(
        OrderItem.objects.filter(
            order__user_id=user_id,
            order__status__in=["confirmed", "shipped", "delivered", "completed"]
        ).values_list("product_id", flat=True)
    )

    # Find users with similar purchase patterns
    similar_users = defaultdict(set)
    product_users = defaultdict(set)

    # Build user-product matrix
    order_items = OrderItem.objects.filter(
        order__status__in=["confirmed", "shipped", "delivered", "completed"]
    ).select_related("order")

    for item in order_items:
        product_users[item.product_id].add(item.order.user_id)

    # Find similar users (Jaccard similarity)
    for user_product in user_products:
        for other_user in product_users[user_product]:
            if other_user != user_id:
                similar_users[other_user].add(user_product)

    # Score users by similarity
    scored_users = []
    for other_user, common_products in similar_users.items():
        other_products = set(
            OrderItem.objects.filter(
                order__user_id=other_user
            ).values_list("product_id", flat=True)
        )

        # Jaccard similarity
        intersection = len(user_products & other_products)
        union = len(user_products | other_products)
        similarity = intersection / union if union > 0 else 0

        scored_users.append((other_user, similarity))

    # Sort by similarity
    scored_users.sort(key=lambda x: x[1], reverse=True)

    # Get products from similar users that user hasn't purchased
    recommendations = defaultdict(float)
    for other_user, similarity in scored_users[:20]:  # Top 20 similar users
        other_products = OrderItem.objects.filter(
            order__user_id=other_user
        ).values_list("product_id", flat=True)

        for product_id in other_products:
            if product_id not in user_products:
                recommendations[product_id] += similarity

    # Sort by score and return top N
    sorted_recommendations = sorted(
        recommendations.items(),
        key=lambda x: x[1],
        reverse=True
    )[:limit]

    return [product_id for product_id, _ in sorted_recommendations]
