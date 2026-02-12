"""
Recommendation Service

Business logic for product recommendations using collaborative filtering.
"""

from typing import List, Dict, Any, Optional, Tuple
from collections import defaultdict
from decimal import Decimal
from django.db.models import Count, Avg, Q, F
from django.db.models.functions import Coalesce
from django.core.cache import cache
from django.conf import settings
import numpy as np
import logging

logger = logging.getLogger("backend")

from backend.core.models import Product, User, OrderItem


class RecommendationService:
    """Service class for product recommendations."""

    def __init__(self):
        self.cache_prefix = "recommendations"
        self.cache_ttl = 3600  # 1 hour

    def get_personalized_recommendations(
        self,
        user_id: int,
        limit: int = 10,
    ) -> List[Dict[str, Any]]:
        """
        Get personalized product recommendations for a user.

        Args:
            user_id: The user ID
            limit: Maximum number of recommendations

        Returns:
            List of recommended products
        """
        cache_key = f"{self.cache_prefix}:user:{user_id}:{limit}"
        recommendations = cache.get(cache_key)

        if recommendations is None:
            # Get user's order history
            ordered_products = self._get_user_ordered_products(user_id)

            if not ordered_products:
                # New user - return popular products
                recommendations = self._get_popular_products(limit)
            else:
                # Use collaborative filtering
                recommendations = self._collaborative_filtering(
                    user_id,
                    ordered_products,
                    limit,
                )

            # Fallback to content-based if collaborative fails
            if len(recommendations) < limit:
                content_based = self._content_based_filtering(
                    ordered_products,
                    limit - len(recommendations),
                )
                recommendations.extend(content_based)

            # Serialize recommendations
            recommendations = [
                {
                    "product_id": p.id,
                    "product_name": p.name,
                    "product_slug": p.slug,
                    "price": float(p.price),
                    "image": p.main_image.url if p.main_image else "",
                    "rating": float(p.average_rating),
                    "score": float(score) if score is not None else 0,
                }
                for p, score in recommendations[:limit]
            ]

            cache.set(cache_key, recommendations, self.cache_ttl)

        return recommendations

    def get_similar_products(
        self,
        product_id: int,
        limit: int = 10,
    ) -> List[Dict[str, Any]]:
        """
        Get products similar to a given product.

        Args:
            product_id: The product ID
            limit: Maximum number of similar products

        Returns:
            List of similar products
        """
        cache_key = f"{self.cache_prefix}:similar:{product_id}:{limit}"
        similar = cache.get(cache_key)

        if similar is None:
            try:
                product = Product.objects.get(id=product_id)

                # Get products in same category
                category_products = Product.objects.filter(
                    category=product.category,
                    is_active=True,
                ).exclude(id=product_id)

                # Calculate similarity scores
                similar_products = []
                for p in category_products:
                    score = self._calculate_product_similarity(product, p)
                    similar_products.append((p, score))

                # Sort by similarity score
                similar_products.sort(key=lambda x: x[1], reverse=True)

                # Serialize
                similar = [
                    {
                        "product_id": p.id,
                        "product_name": p.name,
                        "product_slug": p.slug,
                        "price": float(p.price),
                        "image": p.main_image.url if p.main_image else "",
                        "rating": float(p.average_rating),
                        "similarity_score": float(score),
                    }
                    for p, score in similar_products[:limit]
                ]

                cache.set(cache_key, similar, self.cache_ttl)

            except Product.DoesNotExist:
                return []

        return similar

    def get_trending_products(self, limit: int = 20) -> List[Dict[str, Any]]:
        """
        Get trending products based on recent order activity.

        Args:
            limit: Maximum number of products

        Returns:
            List of trending products
        """
        cache_key = f"{self.cache_prefix}:trending:{limit}"
        trending = cache.get(cache_key)

        if trending is None:
            from datetime import timedelta
            from django.utils import timezone

            # Get products ordered most frequently in the last 30 days
            thirty_days_ago = timezone.now() - timedelta(days=30)

            trending_products = Product.objects.filter(
                is_active=True,
                orderitem__order__created_at__gte=thirty_days_ago,
            ).annotate(
                order_count=Count("orderitem"),
            ).order_by(
                "-order_count",
                "-average_rating",
            )[:limit]

            trending = [
                {
                    "product_id": p.id,
                    "product_name": p.name,
                    "product_slug": p.slug,
                    "price": float(p.price),
                    "image": p.main_image.url if p.main_image else "",
                    "rating": float(p.average_rating),
                    "order_count": p.order_count,
                }
                for p in trending_products
            ]

            cache.set(cache_key, trending, 600)  # 10 minutes

        return trending

    def get_frequently_bought_together(
        self,
        product_id: int,
        limit: int = 10,
    ) -> List[Dict[str, Any]]:
        """
        Get products frequently bought together with a given product.

        Args:
            product_id: The product ID
            limit: Maximum number of products

        Returns:
            List of related products
        """
        cache_key = f"{self.cache_prefix}:fbt:{product_id}:{limit}"
        fbt = cache.get(cache_key)

        if fbt is None:
            # Find orders containing this product
            order_ids = OrderItem.objects.filter(
                product_id=product_id,
            ).values_list("order_id", flat=True)

            if not order_ids:
                # No order history - return category products
                try:
                    product = Product.objects.get(id=product_id)
                    related = Product.objects.filter(
                        category=product.category,
                        is_active=True,
                    ).exclude(id=product_id)[:limit]
                except Product.DoesNotExist:
                    related = []
            else:
                # Find other products in those orders
                related_products = (
                    OrderItem.objects.filter(
                        order_id__in=order_ids,
                    )
                    .exclude(product_id=product_id)
                    .values("product_id")
                    .annotate(
                        frequency=Count("order_id"),
                    )
                    .order_by("-frequency")[:limit]
                )

                product_ids = [item["product_id"] for item in related_products]
                related = Product.objects.filter(
                    id__in=product_ids,
                    is_active=True,
                )

            fbt = [
                {
                    "product_id": p.id,
                    "product_name": p.name,
                    "product_slug": p.slug,
                    "price": float(p.price),
                    "image": p.main_image.url if p.main_image else "",
                    "rating": float(p.average_rating),
                }
                for p in related
            ]

            cache.set(cache_key, fbt, self.cache_ttl)

        return fbt

    def update_user_recommendations(self, user_id: int) -> None:
        """
        Update recommendations cache for a user.

        Args:
            user_id: The user ID
        """
        # Clear existing cache
        cache.delete_many([
            f"{self.cache_prefix}:user:{user_id}:*",
        ])

        # Pre-compute recommendations for different limits
        for limit in [10, 20, 50]:
            self.get_personalized_recommendations(user_id, limit)

    def _get_user_ordered_products(self, user_id: int) -> List[int]:
        """Get list of product IDs ordered by user."""
        product_ids = OrderItem.objects.filter(
            order__user_id=user_id,
        ).values_list("product_id", flat=True).distinct()

        return list(product_ids)

    def _get_popular_products(self, limit: int) -> List[Tuple[Product, float]]:
        """Get popular products with scores."""
        products = Product.objects.filter(
            is_active=True,
        ).annotate(
            order_count=Coalesce(Count("orderitem"), 0),
        ).order_by(
            "-order_count",
            "-average_rating",
        )[:limit]

        return [(p, float(p.order_count)) for p in products]

    def _collaborative_filtering(
        self,
        user_id: int,
        user_products: List[int],
        limit: int,
    ) -> List[Tuple[Product, float]]:
        """
        Perform collaborative filtering to find similar users and recommend products.

        Args:
            user_id: The user ID
            user_products: List of products the user has ordered
            limit: Maximum number of recommendations

        Returns:
            List of (product, score) tuples
        """
        # Find users with similar order patterns
        similar_users = self._find_similar_users(user_id, user_products)

        if not similar_users:
            return []

        # Get products ordered by similar users that this user hasn't ordered
        product_scores = defaultdict(float)

        for similar_user_id, similarity_score in similar_users[:20]:
            # Get products ordered by similar user
            their_products = OrderItem.objects.filter(
                order__user_id=similar_user_id,
            ).values_list("product_id", flat=True).distinct()

            # Score products this user hasn't ordered
            for product_id in their_products:
                if product_id not in user_products:
                    product_scores[product_id] += similarity_score

        # Sort by score and get products
        sorted_products = sorted(
            product_scores.items(),
            key=lambda x: x[1],
            reverse=True,
        )[:limit]

        # Fetch product objects
        product_ids = [pid for pid, _ in sorted_products]
        products = Product.objects.filter(
            id__in=product_ids,
            is_active=True,
        )

        # Create (product, score) tuples
        product_dict = {p.id: p for p in products}
        return [
            (product_dict[pid], score)
            for pid, score in sorted_products
            if pid in product_dict
        ]

    def _find_similar_users(
        self,
        user_id: int,
        user_products: List[int],
    ) -> List[Tuple[int, float]]:
        """
        Find users with similar order patterns using Jaccard similarity.

        Args:
            user_id: The user ID
            user_products: List of products the user has ordered

        Returns:
            List of (user_id, similarity_score) tuples
        """
        user_products_set = set(user_products)

        # Get other users who have ordered any of these products
        other_users = OrderItem.objects.filter(
            product_id__in=user_products,
        ).exclude(
            order__user_id=user_id,
        ).values_list(
            "order__user_id",
            flat=True,
        ).distinct()

        similar_users = []

        for other_user_id in other_users[:100]:  # Limit to 100 users for performance
            # Get products ordered by other user
            their_products = set(
                OrderItem.objects.filter(
                    order__user_id=other_user_id,
                ).values_list("product_id", flat=True).distinct()
            )

            # Calculate Jaccard similarity
            intersection = len(user_products_set & their_products)
            union = len(user_products_set | their_products)

            if union > 0:
                similarity = intersection / union
                if similarity > 0.1:  # Minimum similarity threshold
                    similar_users.append((other_user_id, similarity))

        # Sort by similarity score
        similar_users.sort(key=lambda x: x[1], reverse=True)

        return similar_users

    def _content_based_filtering(
        self,
        user_products: List[int],
        limit: int,
    ) -> List[Tuple[Product, float]]:
        """
        Content-based filtering using product attributes.

        Args:
            user_products: List of products the user has ordered
            limit: Maximum number of recommendations

        Returns:
            List of (product, score) tuples
        """
        if not user_products:
            return []

        # Get categories of user's ordered products
        product_categories = Product.objects.filter(
            id__in=user_products,
        ).values_list("category_id", flat=True).distinct()

        # Get popular products in those categories that user hasn't ordered
        recommended = Product.objects.filter(
            category_id__in=product_categories,
            is_active=True,
        ).exclude(id__in=user_products).annotate(
            order_count=Coalesce(Count("orderitem"), 0),
        ).order_by(
            "-order_count",
            "-average_rating",
        )[:limit]

        return [(p, float(p.order_count)) for p in recommended]

    def _calculate_product_similarity(
        self,
        product1: Product,
        product2: Product,
    ) -> float:
        """
        Calculate similarity score between two products.

        Args:
            product1: First product
            product2: Second product

        Returns:
            Similarity score between 0 and 1
        """
        score = 0.0

        # Same category
        if product1.category_id == product2.category_id:
            score += 0.3

        # Same vendor
        if product1.vendor_id == product2.vendor_id:
            score += 0.2

        # Price similarity
        price_diff = abs(float(product1.price) - float(product2.price))
        max_price = max(float(product1.price), float(product2.price))
        if max_price > 0:
            price_similarity = 1 - (price_diff / max_price)
            score += price_similarity * 0.2

        # Rating similarity
        rating_diff = abs(float(product1.average_rating) - float(product2.average_rating))
        rating_similarity = 1 - (rating_diff / 5.0)  # Max rating is 5
        score += rating_similarity * 0.3

        return min(score, 1.0)
