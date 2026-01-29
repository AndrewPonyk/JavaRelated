"""Product views for E-Commerce Platform."""
from django_filters.rest_framework import DjangoFilterBackend
from rest_framework import filters, permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from .models import Category, Product, ProductReview
from .serializers import (
    CategorySerializer,
    ProductCreateSerializer,
    ProductDetailSerializer,
    ProductListSerializer,
    ProductReviewSerializer,
)


class CategoryViewSet(viewsets.ReadOnlyModelViewSet):
    """ViewSet for browsing product categories."""

    queryset = Category.objects.filter(is_active=True, parent__isnull=True)
    serializer_class = CategorySerializer
    permission_classes = [permissions.AllowAny]
    lookup_field = 'slug'


class ProductViewSet(viewsets.ModelViewSet):
    """ViewSet for product operations."""

    permission_classes = [permissions.AllowAny]
    filter_backends = [DjangoFilterBackend, filters.SearchFilter, filters.OrderingFilter]
    filterset_fields = ['category__slug', 'status', 'is_featured']
    search_fields = ['name', 'description', 'sku']
    ordering_fields = ['price', 'created_at', 'name']
    ordering = ['-created_at']
    lookup_field = 'slug'

    def get_queryset(self):
        """Return active products for public, all for vendors."""
        queryset = Product.objects.select_related(
            'category', 'vendor'
        ).prefetch_related(
            'images', 'variants', 'reviews'
        )

        if self.request.user.is_authenticated and hasattr(self.request.user, 'vendor'):
            # Vendors can see their own products regardless of status
            return queryset.filter(vendor=self.request.user.vendor)

        # Public users only see active products
        return queryset.filter(status=Product.Status.ACTIVE)

    def get_serializer_class(self):
        """Return appropriate serializer based on action."""
        if self.action == 'list':
            return ProductListSerializer
        elif self.action in ['create', 'update', 'partial_update']:
            return ProductCreateSerializer
        return ProductDetailSerializer

    def get_permissions(self):
        """Require authentication for write operations."""
        if self.action in ['create', 'update', 'partial_update', 'destroy']:
            return [permissions.IsAuthenticated()]
        return [permissions.AllowAny()]

    @action(detail=True, methods=['get'])
    def reviews(self, request, slug=None):
        """Get reviews for a specific product (paginated)."""
        product = self.get_object()
        reviews = product.reviews.filter(is_approved=True).select_related('user')
        page = self.paginate_queryset(reviews)
        if page is not None:
            serializer = ProductReviewSerializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = ProductReviewSerializer(reviews, many=True)
        return Response(serializer.data)

    @action(detail=True, methods=['post'], permission_classes=[permissions.IsAuthenticated])
    def add_review(self, request, slug=None):
        """Add a review for a product."""
        product = self.get_object()

        # Check if user already reviewed this product
        if ProductReview.objects.filter(product=product, user=request.user).exists():
            return Response(
                {'error': 'You have already reviewed this product.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        serializer = ProductReviewSerializer(data=request.data)
        if serializer.is_valid():
            # TODO: Check if user has purchased this product
            serializer.save(product=product, user=request.user)
            return Response(serializer.data, status=status.HTTP_201_CREATED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

    @action(detail=False, methods=['get'])
    def featured(self, request):
        """Get featured products."""
        products = self.get_queryset().filter(is_featured=True)[:12]
        serializer = ProductListSerializer(products, many=True)
        return Response(serializer.data)

    @action(detail=False, methods=['get'])
    def by_category(self, request):
        """Get products by category (paginated)."""
        category_slug = request.query_params.get('category')
        if not category_slug:
            return Response(
                {'error': 'Category slug required.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        products = self.get_queryset().filter(category__slug=category_slug)
        page = self.paginate_queryset(products)
        if page is not None:
            serializer = ProductListSerializer(page, many=True)
            return self.get_paginated_response(serializer.data)

        serializer = ProductListSerializer(products, many=True)
        return Response(serializer.data)
