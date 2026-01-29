"""Recommendation views for E-Commerce Platform."""
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import UserInteraction
from .services import RecommendationService


def validate_positive_int(value: str, default: int, max_value: int) -> int:
    """Safely convert and validate a positive integer parameter."""
    try:
        result = int(value)
        if result < 1:
            return default
        return min(result, max_value)
    except (ValueError, TypeError):
        return default


class PersonalizedRecommendationsView(APIView):
    """View for personalized product recommendations."""

    permission_classes = [permissions.IsAuthenticated]
    MAX_LIMIT = 50

    def get(self, request):
        """Get personalized recommendations for the user."""
        limit = validate_positive_int(
            request.query_params.get('limit', '10'), 10, self.MAX_LIMIT
        )

        recommendations = RecommendationService.get_personalized_recommendations(
            user=request.user,
            limit=limit
        )

        return Response({
            'recommendations': recommendations,
            'algorithm': 'collaborative_filtering'
        })


class SimilarProductsView(APIView):
    """View for similar product recommendations."""

    permission_classes = [permissions.AllowAny]
    MAX_LIMIT = 24

    def get(self, request, product_id):
        """Get products similar to a given product."""
        limit = validate_positive_int(
            request.query_params.get('limit', '6'), 6, self.MAX_LIMIT
        )

        similar_products = RecommendationService.get_similar_products(
            product_id=product_id,
            limit=limit
        )

        return Response({
            'product_id': product_id,
            'similar_products': similar_products
        })


class FrequentlyBoughtTogetherView(APIView):
    """View for frequently bought together recommendations."""

    permission_classes = [permissions.AllowAny]
    MAX_LIMIT = 12

    def get(self, request, product_id):
        """Get products frequently bought with a given product."""
        limit = validate_positive_int(
            request.query_params.get('limit', '4'), 4, self.MAX_LIMIT
        )

        products = RecommendationService.get_frequently_bought_together(
            product_id=product_id,
            limit=limit
        )

        return Response({
            'product_id': product_id,
            'frequently_bought_together': products
        })


class TrackInteractionView(APIView):
    """View for tracking user interactions."""

    permission_classes = [permissions.AllowAny]

    def post(self, request):
        """Track a user interaction with a product."""
        product_id = request.data.get('product_id')
        interaction_type = request.data.get('interaction_type')

        if not product_id or not interaction_type:
            return Response(
                {'error': 'product_id and interaction_type required'},
                status=status.HTTP_400_BAD_REQUEST
            )

        valid_types = [t[0] for t in UserInteraction.InteractionType.choices]
        if interaction_type not in valid_types:
            return Response(
                {'error': f'Invalid interaction_type. Must be one of: {valid_types}'},
                status=status.HTTP_400_BAD_REQUEST
            )

        try:
            RecommendationService.track_interaction(
                user=request.user if request.user.is_authenticated else None,
                session_key=request.session.session_key,
                product_id=product_id,
                interaction_type=interaction_type
            )
            return Response({'status': 'tracked'}, status=status.HTTP_201_CREATED)
        except Exception as e:
            return Response(
                {'error': str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )


class TrendingProductsView(APIView):
    """View for trending/popular products."""

    permission_classes = [permissions.AllowAny]
    MAX_LIMIT = 48
    MAX_DAYS = 90

    def get(self, request):
        """Get trending products based on recent interactions."""
        limit = validate_positive_int(
            request.query_params.get('limit', '12'), 12, self.MAX_LIMIT
        )
        days = validate_positive_int(
            request.query_params.get('days', '7'), 7, self.MAX_DAYS
        )

        products = RecommendationService.get_trending_products(
            limit=limit,
            days=days
        )

        return Response({
            'trending_products': products,
            'period_days': days
        })
