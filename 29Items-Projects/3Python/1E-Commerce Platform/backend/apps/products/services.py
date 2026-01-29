"""Product service layer for E-Commerce Platform."""
from decimal import Decimal
from typing import Optional
from uuid import UUID

from django.conf import settings
from django.core.files.base import ContentFile
from django.db import transaction
from django.db.models import Avg, Count, Q
from django.utils.text import slugify

from .models import Category, Product, ProductImage, ProductReview, ProductVariant


class ProductService:
    """Service class for product-related business logic."""

    @staticmethod
    def get_product_with_related(slug: str) -> Optional[Product]:
        """Get product with all related data optimized."""
        return Product.objects.select_related(
            'category', 'vendor'
        ).prefetch_related(
            'images', 'variants', 'reviews'
        ).filter(
            slug=slug,
            status=Product.Status.ACTIVE
        ).first()

    @staticmethod
    def get_product_by_id(product_id: UUID) -> Optional[Product]:
        """Get product by ID with related data."""
        return Product.objects.select_related(
            'category', 'vendor'
        ).prefetch_related(
            'images', 'variants', 'reviews__user'
        ).filter(id=product_id).first()

    @staticmethod
    def get_category_products(category_slug: str, include_children: bool = True):
        """Get all products in a category, optionally including children."""
        category = Category.objects.filter(slug=category_slug).first()
        if not category:
            return Product.objects.none()

        if include_children:
            # Get all descendant category IDs
            category_ids = [category.id]
            children = category.children.filter(is_active=True)
            category_ids.extend(children.values_list('id', flat=True))
            return Product.objects.filter(
                category_id__in=category_ids,
                status=Product.Status.ACTIVE
            )

        return Product.objects.filter(
            category=category,
            status=Product.Status.ACTIVE
        )

    @staticmethod
    def calculate_product_stats(product: Product) -> dict:
        """Calculate product statistics."""
        reviews = product.reviews.filter(is_approved=True)
        stats = reviews.aggregate(
            avg=Avg('rating'),
            count=Count('id')
        )

        rating_dist = reviews.values('rating').annotate(
            count=Count('id')
        ).order_by('rating')

        # Convert to dict for easy access
        distribution = {i: 0 for i in range(1, 6)}
        for item in rating_dist:
            distribution[item['rating']] = item['count']

        return {
            'avg_rating': float(stats['avg'] or 0),
            'review_count': stats['count'],
            'rating_distribution': distribution,
        }

    @staticmethod
    @transaction.atomic
    def create_product_with_variants(
        vendor,
        product_data: dict,
        variants_data: list[dict] = None,
        images_data: list[dict] = None
    ) -> Product:
        """Create a product with its variants and images."""
        # Generate slug if not provided
        if 'slug' not in product_data or not product_data['slug']:
            base_slug = slugify(product_data['name'])
            slug = base_slug
            counter = 1
            while Product.objects.filter(slug=slug).exists():
                slug = f"{base_slug}-{counter}"
                counter += 1
            product_data['slug'] = slug

        # Generate SKU if not provided
        if 'sku' not in product_data or not product_data['sku']:
            import random
            import string
            sku = ''.join(random.choices(string.ascii_uppercase + string.digits, k=8))
            while Product.objects.filter(sku=sku).exists():
                sku = ''.join(random.choices(string.ascii_uppercase + string.digits, k=8))
            product_data['sku'] = sku

        # Create product
        product = Product.objects.create(vendor=vendor, **product_data)

        # Create variants
        if variants_data:
            for variant_data in variants_data:
                ProductVariant.objects.create(product=product, **variant_data)

        # Create images
        if images_data:
            for idx, image_data in enumerate(images_data):
                is_primary = image_data.pop('is_primary', idx == 0)
                ProductImage.objects.create(
                    product=product,
                    is_primary=is_primary,
                    display_order=idx,
                    **image_data
                )

        # Create initial inventory item
        from apps.inventory.services import InventoryService
        InventoryService.add_stock(
            product=product,
            variant=None,
            quantity=product_data.get('initial_stock', 0),
            notes='Initial stock on product creation'
        )

        # Index in Elasticsearch
        from celery_tasks.search_tasks import index_product
        index_product.delay(str(product.id))

        return product

    @staticmethod
    @transaction.atomic
    def update_product(product: Product, update_data: dict) -> Product:
        """Update product information."""
        allowed_fields = [
            'name', 'description', 'short_description', 'price',
            'compare_at_price', 'cost_price', 'category', 'status',
            'is_featured', 'weight', 'meta_title', 'meta_description'
        ]

        for field in allowed_fields:
            if field in update_data:
                setattr(product, field, update_data[field])

        # Regenerate slug if name changed
        if 'name' in update_data:
            base_slug = slugify(update_data['name'])
            slug = base_slug
            counter = 1
            while Product.objects.filter(slug=slug).exclude(id=product.id).exists():
                slug = f"{base_slug}-{counter}"
                counter += 1
            product.slug = slug

        product.save()

        # Update search index
        from celery_tasks.search_tasks import index_product
        index_product.delay(str(product.id))

        return product

    @staticmethod
    def get_similar_products(product: Product, limit: int = 6):
        """Get similar products based on category and attributes."""
        return Product.objects.filter(
            category=product.category,
            status=Product.Status.ACTIVE
        ).exclude(
            id=product.id
        ).select_related('category', 'vendor').prefetch_related(
            'images'
        ).order_by('-created_at')[:limit]

    @staticmethod
    @transaction.atomic
    def apply_bulk_discount(
        product_ids: list,
        discount_percentage: Decimal
    ) -> int:
        """Apply a percentage discount to multiple products."""
        if discount_percentage <= 0 or discount_percentage > 100:
            raise ValueError("Discount percentage must be between 0 and 100.")

        products = Product.objects.filter(id__in=product_ids)
        updated_count = 0

        for product in products:
            # Set compare_at_price to current price if not already set
            if not product.compare_at_price:
                product.compare_at_price = product.price

            # Calculate new discounted price
            discount_amount = product.price * (discount_percentage / 100)
            product.price = (product.price - discount_amount).quantize(Decimal('0.01'))
            product.save()
            updated_count += 1

        return updated_count

    @staticmethod
    @transaction.atomic
    def remove_bulk_discount(product_ids: list) -> int:
        """Remove discounts from multiple products."""
        products = Product.objects.filter(
            id__in=product_ids,
            compare_at_price__isnull=False
        )
        updated_count = 0

        for product in products:
            product.price = product.compare_at_price
            product.compare_at_price = None
            product.save()
            updated_count += 1

        return updated_count

    @staticmethod
    def search_products(query: str, filters: dict = None):
        """Search products with optional filters (fallback to DB search)."""
        queryset = Product.objects.filter(
            status=Product.Status.ACTIVE
        ).filter(
            Q(name__icontains=query) |
            Q(description__icontains=query) |
            Q(sku__icontains=query)
        )

        if filters:
            if 'category' in filters and filters['category']:
                queryset = queryset.filter(category__slug=filters['category'])
            if 'vendor' in filters and filters['vendor']:
                queryset = queryset.filter(vendor__slug=filters['vendor'])
            if 'min_price' in filters and filters['min_price']:
                queryset = queryset.filter(price__gte=filters['min_price'])
            if 'max_price' in filters and filters['max_price']:
                queryset = queryset.filter(price__lte=filters['max_price'])
            if 'min_rating' in filters and filters['min_rating']:
                queryset = queryset.annotate(
                    avg_rating=Avg('reviews__rating')
                ).filter(avg_rating__gte=filters['min_rating'])

        return queryset.select_related('category', 'vendor').prefetch_related('images')

    @staticmethod
    def get_featured_products(limit: int = 8):
        """Get featured products."""
        return Product.objects.filter(
            status=Product.Status.ACTIVE,
            is_featured=True
        ).select_related('category', 'vendor').prefetch_related(
            'images'
        ).order_by('-created_at')[:limit]

    @staticmethod
    def get_new_arrivals(limit: int = 8):
        """Get newest products."""
        return Product.objects.filter(
            status=Product.Status.ACTIVE
        ).select_related('category', 'vendor').prefetch_related(
            'images'
        ).order_by('-created_at')[:limit]

    @staticmethod
    def get_best_sellers(limit: int = 8):
        """Get best selling products based on order count."""
        from django.db.models import Sum
        return Product.objects.filter(
            status=Product.Status.ACTIVE
        ).annotate(
            total_sold=Sum('orderitem__quantity')
        ).select_related('category', 'vendor').prefetch_related(
            'images'
        ).order_by('-total_sold')[:limit]

    @staticmethod
    @transaction.atomic
    def add_review(product: Product, user, review_data: dict) -> ProductReview:
        """Add a review to a product."""
        # Check if user already reviewed this product
        existing = ProductReview.objects.filter(product=product, user=user).exists()
        if existing:
            raise ValueError("You have already reviewed this product.")

        # Check if user has purchased this product
        from apps.checkout.models import OrderItem
        has_purchased = OrderItem.objects.filter(
            product=product,
            order__user=user,
            order__status='delivered'
        ).exists()

        review = ProductReview.objects.create(
            product=product,
            user=user,
            rating=review_data['rating'],
            title=review_data.get('title', ''),
            comment=review_data.get('comment', ''),
            is_verified_purchase=has_purchased,
            is_approved=True  # Auto-approve for now, can be changed to moderation
        )

        # Update product rating cache if exists
        ProductService._update_product_rating(product)

        return review

    @staticmethod
    def _update_product_rating(product: Product):
        """Update product's cached rating."""
        stats = ProductService.calculate_product_stats(product)
        product.avg_rating = Decimal(str(stats['avg_rating']))
        product.review_count = stats['review_count']
        product.save(update_fields=['avg_rating', 'review_count'])


class CategoryService:
    """Service class for category-related business logic."""

    @staticmethod
    def get_all_categories(include_inactive: bool = False):
        """Get all categories."""
        queryset = Category.objects.all()
        if not include_inactive:
            queryset = queryset.filter(is_active=True)
        return queryset.order_by('name')

    @staticmethod
    def get_root_categories():
        """Get top-level categories."""
        return Category.objects.filter(
            parent__isnull=True,
            is_active=True
        ).order_by('name')

    @staticmethod
    def get_category_tree():
        """Get category tree structure."""
        root_categories = CategoryService.get_root_categories()
        tree = []

        for category in root_categories:
            tree.append({
                'id': category.id,
                'name': category.name,
                'slug': category.slug,
                'image': category.image.url if category.image else None,
                'product_count': Product.objects.filter(
                    category=category,
                    status=Product.Status.ACTIVE
                ).count(),
                'children': [
                    {
                        'id': child.id,
                        'name': child.name,
                        'slug': child.slug,
                        'product_count': Product.objects.filter(
                            category=child,
                            status=Product.Status.ACTIVE
                        ).count(),
                    }
                    for child in category.children.filter(is_active=True)
                ]
            })

        return tree

    @staticmethod
    @transaction.atomic
    def create_category(category_data: dict) -> Category:
        """Create a new category."""
        if 'slug' not in category_data or not category_data['slug']:
            base_slug = slugify(category_data['name'])
            slug = base_slug
            counter = 1
            while Category.objects.filter(slug=slug).exists():
                slug = f"{base_slug}-{counter}"
                counter += 1
            category_data['slug'] = slug

        return Category.objects.create(**category_data)
