"""
Product Service

Business logic for product operations.
"""

from typing import List, Optional
from django.core.cache import cache
from django.db import transaction
from django.db.models import Q, F, Count, Avg

from backend.core.models import Product, Category


class ProductService:
    """Service class for product-related business logic."""

    def __init__(self):
        self.cache_prefix = "product"
        self.cache_ttl = 300  # 5 minutes

    def get_product_by_id(self, product_id: int) -> Optional[Product]:
        """
        Get product by ID with caching.

        Args:
            product_id: The product ID

        Returns:
            Product object or None
        """
        cache_key = f"{self.cache_prefix}:{product_id}"
        product = cache.get(cache_key)

        if product is None:
            try:
                product = Product.objects.get(id=product_id, is_active=True)
                cache.set(cache_key, product, self.cache_ttl)
            except Product.DoesNotExist:
                return None

        return product

    def get_product_by_slug(self, slug: str) -> Optional[Product]:
        """
        Get product by slug with caching.

        Args:
            slug: The product slug

        Returns:
            Product object or None
        """
        cache_key = f"{self.cache_prefix}:slug:{slug}"
        product = cache.get(cache_key)

        if product is None:
            try:
                product = Product.objects.get(slug=slug, is_active=True)
                cache.set(cache_key, product, self.cache_ttl)
            except Product.DoesNotExist:
                return None

        return product

    def search_products(
        self,
        query: Optional[str] = None,
        category_id: Optional[int] = None,
        vendor_id: Optional[int] = None,
        min_price: Optional[float] = None,
        max_price: Optional[float] = None,
        in_stock: bool = False,
        sort_by: str = "created_at",
        page: int = 1,
        page_size: int = 20,
    ) -> tuple[List[Product], int]:
        """
        Search products with filters.

        Args:
            query: Search query string
            category_id: Filter by category
            vendor_id: Filter by vendor
            min_price: Minimum price
            max_price: Maximum price
            in_stock: Only show in-stock items
            sort_by: Sort field
            page: Page number
            page_size: Items per page

        Returns:
            Tuple of (products list, total count)
        """
        queryset = Product.objects.filter(is_active=True)

        # Apply search
        if query:
            queryset = queryset.filter(
                Q(name__icontains=query) |
                Q(description__icontains=query) |
                Q(short_description__icontains=query)
            )

        # Apply filters
        if category_id:
            queryset = queryset.filter(category_id=category_id)
        if vendor_id:
            queryset = queryset.filter(vendor_id=vendor_id)
        if min_price is not None:
            queryset = queryset.filter(price__gte=min_price)
        if max_price is not None:
            queryset = queryset.filter(price__lte=max_price)
        if in_stock:
            queryset = queryset.filter(stock__gt=0)

        # Apply sorting
        sort_mapping = {
            "name": "name",
            "price_asc": "price",
            "price_desc": "-price",
            "rating": "-average_rating",
            "newest": "-created_at",
            "popular": "-review_count",
        }
        queryset = queryset.order_by(sort_mapping.get(sort_by, "-created_at"))

        # Get total count before pagination
        total = queryset.count()

        # Apply pagination
        start = (page - 1) * page_size
        end = start + page_size
        products = list(queryset[start:end])

        return products, total

    def get_featured_products(self, limit: int = 20) -> List[Product]:
        """
        Get featured products.

        Args:
            limit: Maximum number of products to return

        Returns:
            List of featured products
        """
        cache_key = f"{self.cache_prefix}:featured:{limit}"
        products = cache.get(cache_key)

        if products is None:
            products = list(
                Product.objects.filter(is_active=True, is_featured=True)[:limit]
            )
            cache.set(cache_key, products, self.cache_ttl)

        return products

    def get_deal_products(self, limit: int = 20) -> List[Product]:
        """
        Get products on sale (discounted).

        Args:
            limit: Maximum number of products to return

        Returns:
            List of discounted products
        """
        cache_key = f"{self.cache_prefix}:deals:{limit}"
        products = cache.get(cache_key)

        if products is None:
            products = list(
                Product.objects.filter(
                    is_active=True,
                    original_price__isnull=False,
                )
                .exclude(original_price__exact=F("price"))
                .order_by("-original_price")[:limit]
            )
            cache.set(cache_key, products, self.cache_ttl)

        return products

    def get_related_products(self, product: Product, limit: int = 4) -> List[Product]:
        """
        Get related products based on category.

        Args:
            product: The product to find related items for
            limit: Maximum number of products to return

        Returns:
            List of related products
        """
        cache_key = f"{self.cache_prefix}:{product.id}:related:{limit}"
        products = cache.get(cache_key)

        if products is None:
            products = list(
                Product.objects.filter(
                    category=product.category,
                    is_active=True,
                )
                .exclude(id=product.id)
                .order_by("-average_rating")[:limit]
            )
            cache.set(cache_key, products, self.cache_ttl)

        return products

    def update_product_rating(self, product_id: int) -> None:
        """
        Recalculate and update product rating.

        Args:
            product_id: The product ID
        """
        from backend.core.models import Review
        from decimal import Decimal

        product = Product.objects.get(id=product_id)
        reviews = Review.objects.filter(product=product, is_approved=True)

        if reviews.exists():
            total = sum(r.rating for r in reviews)
            avg = Decimal(total) / Decimal(reviews.count())
            product.average_rating = avg.quantize(Decimal("0.1"))
            product.review_count = reviews.count()
        else:
            product.average_rating = Decimal("0.0")
            product.review_count = 0

        product.save(update_fields=["average_rating", "review_count"])

        # Invalidate cache
        cache.delete(f"{self.cache_prefix}:{product_id}")
        cache.delete(f"{self.cache_prefix}:slug:{product.slug}")

    def decrease_stock(self, product_id: int, quantity: int) -> bool:
        """
        Decrease product stock atomically.

        Args:
            product_id: The product ID
            quantity: Quantity to decrease

        Returns:
            True if successful, False otherwise
        """
        with transaction.atomic():
            product = Product.objects.select_for_update().get(id=product_id)

            if product.stock < quantity:
                return False

            product.stock -= quantity
            product.save(update_fields=["stock"])

            # Invalidate cache
            cache.delete(f"{self.cache_prefix}:{product_id}")
            cache.delete(f"{self.cache_prefix}:slug:{product.slug}")

            return True

    def increase_stock(self, product_id: int, quantity: int) -> None:
        """
        Increase product stock.

        Args:
            product_id: The product ID
            quantity: Quantity to increase
        """
        with transaction.atomic():
            product = Product.objects.select_for_update().get(id=product_id)
            product.stock += quantity
            product.save(update_fields=["stock"])

            # Invalidate cache
            cache.delete(f"{self.cache_prefix}:{product_id}")
            cache.delete(f"{self.cache_prefix}:slug:{product.slug}")


class ReviewService:
    """Service for review-related operations."""

    def get_product_reviews(self, product_id: int, approved_only: bool = True) -> list:
        """Get reviews for a product."""
        from backend.core.models import Review

        queryset = Review.objects.filter(product=product_id)
        if approved_only:
            queryset = queryset.filter(is_approved=True)

        return [
            {
                "id": r.id,
                "user": r.user.get_full_name(),
                "rating": r.rating,
                "title": r.title,
                "comment": r.comment,
                "helpful_count": r.helpful_count,
                "is_verified_purchase": r.is_verified_purchase,
                "created_at": r.created_at.isoformat(),
            }
            for r in queryset
        ]

    def get_user_reviews(self, user_id: int) -> list:
        """Get reviews by a user."""
        from backend.core.models import Review

        reviews = Review.objects.filter(user=user_id).select_related("product")

        return [
            {
                "id": r.id,
                "product": {
                    "id": r.product.id,
                    "name": r.product.name,
                    "slug": r.product.slug,
                    "image": r.product.main_image.url if r.product.main_image else None,
                },
                "rating": r.rating,
                "title": r.title,
                "comment": r.comment,
                "can_edit": r.can_edit,
                "created_at": r.created_at.isoformat(),
            }
            for r in reviews
        ]

    def create_review(self, user_id: int, product_id: int, rating: int,
                      title: str, comment: str, order_id: int = None) -> dict:
        """Create a new review."""
        from backend.core.models import Review, Order

        # Check if user already reviewed this product
        if Review.objects.filter(user_id=user_id, product_id=product_id).exists():
            raise ValueError("You have already reviewed this product")

        # If order provided, validate it
        is_verified = False
        if order_id:
            order = Order.objects.get(id=order_id)
            if order.user_id != user_id:
                raise ValueError("Order does not belong to you")
            if order.status not in ["delivered", "completed"]:
                raise ValueError("Reviews can only be created for delivered orders")
            is_verified = True

        review = Review.objects.create(
            user_id=user_id,
            product_id=product_id,
            order_id=order_id,
            rating=rating,
            title=title,
            comment=comment,
            is_verified_purchase=is_verified,
        )

        return {
            "id": review.id,
            "rating": review.rating,
            "title": review.title,
            "comment": review.comment,
            "is_verified_purchase": review.is_verified_purchase,
            "created_at": review.created_at.isoformat(),
        }

    def update_review(self, review_id: int, user_id: int,
                      title: str = None, comment: str = None, rating: int = None) -> dict:
        """Update an existing review."""
        from backend.core.models import Review

        review = Review.objects.get(id=review_id, user_id=user_id)

        if not review.can_edit:
            raise ValueError("Reviews can only be edited within 30 days")

        if title is not None:
            review.title = title
        if comment is not None:
            review.comment = comment
        if rating is not None:
            review.rating = rating

        review.save()

        return {
            "id": review.id,
            "rating": review.rating,
            "title": review.title,
            "comment": review.comment,
        }

    def delete_review(self, review_id: int, user_id: int) -> bool:
        """Delete a review."""
        from backend.core.models import Review

        review = Review.objects.get(id=review_id, user_id=user_id)
        review.delete()
        return True

    def mark_review_helpful(self, review_id: int, user_id: int, helpful: bool) -> dict:
        """Mark review as helpful or not."""
        from backend.core.models import Review

        review = Review.objects.get(id=review_id)
        review.mark_helpful(user_id, helpful)

        return {
            "helpful_count": review.helpful_count,
            "not_helpful_count": review.not_helpful_count,
        }


def get_recommended_products(user_id: int, limit: int = 10) -> list:
    """
    Get recommended products for a user based on purchase history.

    Args:
        user_id: The user ID
        limit: Maximum number of products to return

    Returns:
        List of recommended product IDs
    """
    from backend.core.models import Order, OrderItem

    # Get products from user's orders
    purchased_product_ids = OrderItem.objects.filter(
        order__user_id=user_id,
        order__status__in=["confirmed", "shipped", "delivered", "completed"]
    ).values_list("product_id", flat=True).distinct()

    # Find products purchased by other users who bought similar items
    similar_users_orders = OrderItem.objects.filter(
        product_id__in=purchased_product_ids
    ).exclude(order__user_id=user_id).values_list("order_id", flat=True).distinct()

    recommended_product_ids = OrderItem.objects.filter(
        order_id__in=similar_users_orders
    ).exclude(
        product_id__in=purchased_product_ids
    ).values_list("product_id", flat=True).distinct()[:limit]

    return list(recommended_product_ids)


def get_frequently_bought_together(product_id: int, limit: int = 5) -> list:
    """
    Get products frequently bought together with the given product.

    Args:
        product_id: The product ID
        limit: Maximum number of products to return

    Returns:
        List of related product IDs
    """
    from backend.core.models import OrderItem, Order

    # Get orders containing this product
    order_ids = OrderItem.objects.filter(
        product_id=product_id
    ).values_list("order_id", flat=True)

    # Get other products in those orders
    product_counts = OrderItem.objects.filter(
        order_id__in=order_ids
    ).exclude(
        product_id=product_id
    ).values("product_id").annotate(
        count=models.Count("product_id")
    ).order_by("-count")[:limit]

    return [p["product_id"] for p in product_counts]
