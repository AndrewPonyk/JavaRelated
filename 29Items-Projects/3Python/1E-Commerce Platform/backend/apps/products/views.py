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


# |su:67 ModelViewSet provides full CRUD; ReadOnlyModelViewSet only provides list/retrieve
class ProductViewSet(viewsets.ModelViewSet):
    """ViewSet for product operations."""

    permission_classes = [permissions.AllowAny]
    # |su:68 Filter backends enable URL query params:
    # - DjangoFilterBackend: ?category__slug=phones (exact match)
    # - SearchFilter: ?search=iphone (searches across search_fields)
    # - OrderingFilter: ?ordering=-price (sort by price descending)
    filter_backends = [DjangoFilterBackend, filters.SearchFilter, filters.OrderingFilter]
    filterset_fields = ['category__slug', 'status', 'is_featured']
    search_fields = ['name', 'description', 'sku']
    ordering_fields = ['price', 'created_at', 'name']
    ordering = ['-created_at']
    # |su:69 lookup_field='slug': use /products/iphone-15/ instead of /products/uuid/
    lookup_field = 'slug'

    def get_queryset(self):
        """Return active products for public, all for vendors."""
        # |su:70 select_related vs prefetch_related:
        # - select_related: JOIN for ForeignKey/OneToOne (single query)
        # - prefetch_related: separate query for ManyToMany/reverse FK
        queryset = Product.objects.select_related(
            'category', 'vendor'  # FK fields: one JOIN query
        ).prefetch_related(
            'images', 'variants', 'reviews'  # Reverse FK: separate queries
        )

        if self.request.user.is_authenticated and hasattr(self.request.user, 'vendor'):
            # Vendors can see their own products regardless of status
            return queryset.filter(vendor=self.request.user.vendor)

        # Public users only see active products
        return queryset.filter(status=Product.Status.ACTIVE)

    # |su:71 Different serializers for different actions:
    # - List: lightweight (name, price, thumbnail) - fast for browsing
    # - Detail: full data (all images, reviews, variants) - single product page
    # - Create/Update: write-focused (accepts input, validates)
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
