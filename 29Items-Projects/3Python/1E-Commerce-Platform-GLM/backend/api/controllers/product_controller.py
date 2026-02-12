"""
Product Controller (ViewSets)

REST API endpoints for products.
"""

from rest_framework import viewsets, filters, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticatedOrReadOnly
from django_filters.rest_framework import DjangoFilterBackend
from django.shortcuts import get_object_or_404
from django.db import models

from backend.core.models import Product, Category, Review
from backend.api.serializers import (
    ProductSerializer,
    ProductDetailSerializer,
    CategorySerializer,
    ReviewSerializer,
    ReviewDetailSerializer,
    ReviewCreateSerializer,
)
from backend.api.services.product_service import ProductService
from backend.api.pagination import StandardResultsSetPagination


class ProductViewSet(viewsets.ReadOnlyModelViewSet):
    """
    ViewSet for the Product model.

    Provides list, retrieve, and custom actions for products.
    """

    queryset = Product.objects.filter(is_active=True)
    permission_classes = [AllowAny]
    pagination_class = StandardResultsSetPagination
    filter_backends = [DjangoFilterBackend, filters.SearchFilter, filters.OrderingFilter]
    filterset_fields = ["category", "vendor", "is_featured"]
    search_fields = ["name", "description", "short_description"]
    ordering_fields = ["price", "created_at", "name", "average_rating"]
    ordering = ["-created_at"]

    def get_serializer_class(self):
        """Return appropriate serializer based on action."""
        if self.action == "retrieve":
            return ProductDetailSerializer
        return ProductSerializer

    def retrieve(self, request, *args, **kwargs):
        """Get a single product with full details."""
        instance = self.get_object()
        serializer = self.get_serializer(instance)
        return Response(serializer.data)

    @action(detail=True, methods=["get"])
    def related(self, request, pk=None):
        """Get related products for the given product."""
        product = self.get_object()
        related_products = product.get_related_products(limit=4)
        serializer = ProductSerializer(related_products, many=True)
        return Response(serializer.data)

    @action(detail=False, methods=["get"])
    def featured(self, request):
        """Get all featured products."""
        featured = self.queryset.filter(is_featured=True)[:20]
        serializer = ProductSerializer(featured, many=True)
        return Response(serializer.data)

    @action(detail=False, methods=["get"])
    def deals(self, request):
        """Get products on sale (discounted)."""
        deals = self.queryset.filter(original_price__isnull=False).exclude(
            original_price__exact=models.F("price")
        )[:20]
        serializer = ProductSerializer(deals, many=True)
        return Response(serializer.data)

    @action(detail=True, methods=["get"])
    def reviews(self, request, pk=None):
        """Get reviews for a product."""
        product = self.get_object()
        reviews = product.reviews.filter(is_approved=True)

        # Filter by rating if provided
        rating = request.query_params.get("rating")
        if rating:
            reviews = reviews.filter(rating=rating)

        page = self.paginate_queryset(reviews)
        if page is not None:
            serializer = ReviewDetailSerializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = ReviewDetailSerializer(reviews, many=True)
        return Response(serializer.data)

    @action(detail=True, methods=["post"], permission_classes=[IsAuthenticatedOrReadOnly])
    def add_review(self, request, pk=None):
        """Add a review for a product."""
        product = self.get_object()

        # Check if user already reviewed this product
        if Review.objects.filter(user=request.user, product=product).exists():
            return Response(
                {"error": "You have already reviewed this product"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        serializer = ReviewCreateSerializer(
            data=request.data,
            context={"request": request}
        )
        serializer.is_valid(raise_exception=True)

        # Ensure product matches the URL
        serializer.validated_data["product"] = product
        serializer.save(user=request.user)

        # Return full review details
        review = Review.objects.get(id=serializer.instance.id)
        response_serializer = ReviewDetailSerializer(review)
        return Response(response_serializer.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"], permission_classes=[IsAuthenticatedOrReadOnly])
    def review_helpful(self, request, pk=None):
        """Mark a review as helpful or not."""
        product = self.get_object()
        review_id = request.data.get("review_id")
        helpful = request.data.get("helpful", True)

        if not review_id:
            return Response(
                {"error": "review_id is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            review = Review.objects.get(id=review_id, product=product)
            review.mark_helpful(request.user.id, helpful)

            serializer = ReviewDetailSerializer(review)
            return Response(serializer.data)

        except Review.DoesNotExist:
            return Response(
                {"error": "Review not found"},
                status=status.HTTP_404_NOT_FOUND,
            )


class CategoryViewSet(viewsets.ReadOnlyModelViewSet):
    """
    ViewSet for the Category model.
    """

    queryset = Category.objects.filter(is_active=True)
    serializer_class = CategorySerializer
    permission_classes = [AllowAny]
    lookup_field = "slug"

    @action(detail=True, methods=["get"])
    def products(self, request, slug=None):
        """Get all products in this category."""
        category = self.get_object()
        products = category.products.filter(is_active=True)

        # Apply filters from query params
        products = self._apply_filters(products, request.query_params)

        page = self.paginate_queryset(products)
        if page is not None:
            serializer = ProductSerializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = ProductSerializer(products, many=True)
        return Response(serializer.data)

    def _apply_filters(self, queryset, params):
        """Apply filters to the product queryset."""
        # Price range
        min_price = params.get("min_price")
        max_price = params.get("max_price")
        if min_price:
            queryset = queryset.filter(price__gte=min_price)
        if max_price:
            queryset = queryset.filter(price__lte=max_price)

        # In stock
        if params.get("in_stock") == "true":
            queryset = queryset.filter(stock__gt=0)

        # Sorting
        sort_by = params.get("sort_by", "newest")
        sort_mapping = {
            "price_asc": "price",
            "price_desc": "-price",
            "rating": "-average_rating",
            "newest": "-created_at",
            "name": "name",
        }
        if sort_by in sort_mapping:
            queryset = queryset.order_by(sort_mapping[sort_by])

        return queryset
