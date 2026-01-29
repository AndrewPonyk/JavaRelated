"""Recommendation service layer for E-Commerce Platform."""
from datetime import timedelta
from typing import List, Optional
from uuid import UUID

from django.conf import settings
from django.core.cache import cache
from django.db.models import Count, Sum
from django.utils import timezone

from apps.products.models import Product

from .models import ProductSimilarity, RecommendationModel, UserInteraction


class RecommendationService:
    """Service class for recommendation-related business logic."""

    CACHE_TTL = 3600  # 1 hour

    @staticmethod
    def get_personalized_recommendations(
        user,
        limit: int = 10
    ) -> List[dict]:
        """Get personalized recommendations for a user."""
        cache_key = f"recommendations:user:{user.id}:limit:{limit}"
        cached = cache.get(cache_key)
        if cached:
            return cached

        # Get user's interaction history
        user_interactions = UserInteraction.objects.filter(
            user=user
        ).values_list('product_id', flat=True).distinct()

        interacted_products = set(user_interactions)

        # Strategy 1: Similar products to what user has interacted with
        similar_products = ProductSimilarity.objects.filter(
            product_id__in=interacted_products
        ).exclude(
            similar_product_id__in=interacted_products
        ).values('similar_product_id').annotate(
            score=Sum('similarity_score')
        ).order_by('-score')[:limit]

        product_ids = [p['similar_product_id'] for p in similar_products]

        # Fetch product details
        products = Product.objects.filter(
            id__in=product_ids,
            status=Product.Status.ACTIVE
        ).select_related('category')

        # Create ID to product mapping
        product_map = {str(p.id): p for p in products}

        # Build recommendations with scores
        recommendations = []
        for item in similar_products:
            product = product_map.get(str(item['similar_product_id']))
            if product:
                recommendations.append({
                    'id': str(product.id),
                    'name': product.name,
                    'slug': product.slug,
                    'price': float(product.price),
                    'category': product.category.name if product.category else None,
                    'score': item['score'],
                })

        # If not enough recommendations, fall back to trending
        if len(recommendations) < limit:
            trending = RecommendationService.get_trending_products(
                limit=limit - len(recommendations),
                exclude_ids=[r['id'] for r in recommendations]
            )
            recommendations.extend(trending)

        cache.set(cache_key, recommendations, RecommendationService.CACHE_TTL)
        return recommendations

    @staticmethod
    def get_similar_products(
        product_id: UUID,
        limit: int = 6
    ) -> List[dict]:
        """Get products similar to a given product."""
        cache_key = f"similar:product:{product_id}:limit:{limit}"
        cached = cache.get(cache_key)
        if cached:
            return cached

        # Check pre-computed similarities
        similarities = ProductSimilarity.objects.filter(
            product_id=product_id
        ).select_related('similar_product__category').order_by(
            '-similarity_score'
        )[:limit]

        if similarities:
            results = [{
                'id': str(s.similar_product.id),
                'name': s.similar_product.name,
                'slug': s.similar_product.slug,
                'price': float(s.similar_product.price),
                'category': s.similar_product.category.name if s.similar_product.category else None,
                'similarity_score': s.similarity_score,
            } for s in similarities]
        else:
            # Fallback: products in same category
            try:
                product = Product.objects.get(id=product_id)
                similar = Product.objects.filter(
                    category=product.category,
                    status=Product.Status.ACTIVE
                ).exclude(id=product_id)[:limit]

                results = [{
                    'id': str(p.id),
                    'name': p.name,
                    'slug': p.slug,
                    'price': float(p.price),
                    'category': p.category.name if p.category else None,
                    'similarity_score': 0.5,  # Default score for category-based
                } for p in similar]
            except Product.DoesNotExist:
                results = []

        cache.set(cache_key, results, RecommendationService.CACHE_TTL)
        return results

    @staticmethod
    def get_frequently_bought_together(
        product_id: UUID,
        limit: int = 4
    ) -> List[dict]:
        """Get products frequently bought with a given product."""
        from apps.checkout.models import OrderItem

        cache_key = f"bought_together:product:{product_id}:limit:{limit}"
        cached = cache.get(cache_key)
        if cached:
            return cached

        # Find orders containing this product
        orders_with_product = OrderItem.objects.filter(
            product_id=product_id
        ).values_list('order_id', flat=True)

        # Find other products in those orders
        co_purchased = OrderItem.objects.filter(
            order_id__in=orders_with_product
        ).exclude(
            product_id=product_id
        ).values('product_id').annotate(
            frequency=Count('id')
        ).order_by('-frequency')[:limit]

        product_ids = [p['product_id'] for p in co_purchased]
        products = Product.objects.filter(
            id__in=product_ids,
            status=Product.Status.ACTIVE
        ).select_related('category')

        product_map = {str(p.id): p for p in products}

        results = []
        for item in co_purchased:
            product = product_map.get(str(item['product_id']))
            if product:
                results.append({
                    'id': str(product.id),
                    'name': product.name,
                    'slug': product.slug,
                    'price': float(product.price),
                    'category': product.category.name if product.category else None,
                    'co_purchase_count': item['frequency'],
                })

        cache.set(cache_key, results, RecommendationService.CACHE_TTL)
        return results

    @staticmethod
    def get_trending_products(
        limit: int = 12,
        days: int = 7,
        exclude_ids: Optional[List[str]] = None
    ) -> List[dict]:
        """Get trending products based on recent interactions."""
        cache_key = f"trending:days:{days}:limit:{limit}"
        cached = cache.get(cache_key)
        if cached and not exclude_ids:
            return cached

        cutoff = timezone.now() - timedelta(days=days)

        # Weight interactions by type
        trending = UserInteraction.objects.filter(
            created_at__gte=cutoff
        ).values('product_id').annotate(
            score=Sum('implicit_rating')
        ).order_by('-score')

        if exclude_ids:
            trending = trending.exclude(product_id__in=exclude_ids)

        trending = trending[:limit]

        product_ids = [t['product_id'] for t in trending]
        products = Product.objects.filter(
            id__in=product_ids,
            status=Product.Status.ACTIVE
        ).select_related('category')

        product_map = {str(p.id): p for p in products}

        results = []
        for item in trending:
            product = product_map.get(str(item['product_id']))
            if product:
                results.append({
                    'id': str(product.id),
                    'name': product.name,
                    'slug': product.slug,
                    'price': float(product.price),
                    'category': product.category.name if product.category else None,
                    'trending_score': item['score'],
                })

        if not exclude_ids:
            cache.set(cache_key, results, RecommendationService.CACHE_TTL // 2)

        return results

    @staticmethod
    def track_interaction(
        user,
        session_key: str,
        product_id: UUID,
        interaction_type: str
    ) -> UserInteraction:
        """Track a user interaction with a product."""
        return UserInteraction.objects.create(
            user=user,
            product_id=product_id,
            interaction_type=interaction_type,
            session_key=session_key or ''
        )

    @staticmethod
    def invalidate_user_recommendations(user_id: int) -> None:
        """Invalidate cached recommendations for a user."""
        # Clear all cached recommendations for this user
        for limit in [5, 10, 20]:
            cache_key = f"recommendations:user:{user_id}:limit:{limit}"
            cache.delete(cache_key)
