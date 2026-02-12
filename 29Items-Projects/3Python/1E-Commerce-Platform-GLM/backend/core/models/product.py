"""
Product Models

Models for products, categories, and related entities.
"""

from django.db import models
from django.core.cache import cache


class Category(models.Model):
    """
    Product category for organizing products.
    """

    class Meta:
        db_table = "categories"
        verbose_name = "Category"
        verbose_name_plural = "Categories"
        ordering = ["name"]

    name = models.CharField(max_length=200)
    slug = models.SlugField(unique=True, max_length=255)
    description = models.TextField(blank=True)
    image = models.ImageField(upload_to="categories/", blank=True, null=True)
    parent = models.ForeignKey(
        "self",
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        related_name="children",
    )
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return self.name

    @property
    def product_count(self) -> int:
        """Get the count of products in this category."""
        return self.products.filter(is_active=True).count()

    def get_ancestors(self):
        """Get all ancestor categories."""
        ancestors = []
        current = self.parent
        while current:
            ancestors.append(current)
            current = current.parent
        return list(reversed(ancestors))


class Product(models.Model):
    """
    Product model for the e-commerce platform.
    """

    class Meta:
        db_table = "products"
        verbose_name = "Product"
        verbose_name_plural = "Products"
        ordering = ["-created_at"]
        indexes = [
            models.Index(fields=["slug"]),
            models.Index(fields=["price"]),
            models.Index(fields=["-created_at"]),
            models.Index(fields=["is_active", "stock"]),
            models.Index(fields=["category"]),
            models.Index(fields=["vendor"]),
            models.Index(fields=["average_rating"]),
            models.Index(fields=["is_featured"]),
        ]

    # Basic Information
    name = models.CharField(max_length=255)
    slug = models.SlugField(unique=True, max_length=255)
    description = models.TextField()
    short_description = models.CharField(max_length=500, blank=True)

    # Pricing
    price = models.DecimalField(max_digits=10, decimal_places=2)
    original_price = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        null=True,
        blank=True,
        help_text="Original price before discount",
    )

    @property
    def discount_percentage(self) -> int:
        """Calculate discount percentage."""
        if self.original_price and self.original_price > self.price:
            return int(((self.original_price - self.price) / self.original_price) * 100)
        return 0

    # Category
    category = models.ForeignKey(
        Category,
        on_delete=models.SET_NULL,
        null=True,
        related_name="products",
    )

    # Vendor
    vendor = models.ForeignKey(
        "Vendor",
        on_delete=models.CASCADE,
        related_name="products",
    )

    # Inventory
    stock = models.IntegerField(default=0)
    is_active = models.BooleanField(default=True)
    is_featured = models.BooleanField(default=False)

    # Images
    main_image = models.ImageField(upload_to="products/")
    images = models.ManyToManyField("ProductImage", related_name="product_images", blank=True)

    # Specifications (JSON field for flexible key-value pairs)
    specifications = models.JSONField(default=dict, blank=True)

    # Ratings
    average_rating = models.DecimalField(
        max_digits=3,
        decimal_places=2,
        default=0.0,
    )
    review_count = models.IntegerField(default=0)

    # SEO
    meta_title = models.CharField(max_length=255, blank=True)
    meta_description = models.TextField(blank=True)
    meta_keywords = models.CharField(max_length=500, blank=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return self.name

    def save(self, *args, **kwargs):
        """Override save to invalidate cache and update search index."""
        is_new = self.pk is None
        super().save(*args, **kwargs)

        # Invalidate product cache
        cache.delete(f"product:{self.id}")
        cache.delete(f"product:{self.slug}")

        # Update Elasticsearch index (async via Celery)
        from backend.tasks.search_tasks import update_product_search_index

        update_product_search_index.delay(self.id)

    def get_absolute_url(self):
        """Get the URL for this product."""
        # TODO: Implement proper URL routing
        return f"/products/{self.slug}"

    def is_in_stock(self) -> bool:
        """Check if product is in stock."""
        return self.stock > 0

    def get_related_products(self, limit=4):
        """Get related products based on category."""
        return (
            Product.objects.filter(
                category=self.category,
                is_active=True,
            )
            .exclude(id=self.id)
            .order_by("-average_rating")[:limit]
        )


class ProductImage(models.Model):
    """
    Additional images for products.
    """

    class Meta:
        db_table = "product_images"
        verbose_name = "Product Image"
        verbose_name_plural = "Product Images"
        ordering = ["order"]

    product = models.ForeignKey(
        Product,
        on_delete=models.CASCADE,
        related_name="product_images",
    )
    image = models.ImageField(upload_to="products/")
    alt_text = models.CharField(max_length=255, blank=True)
    order = models.IntegerField(default=0)

    def __str__(self) -> str:
        return f"{self.product.name} - Image {self.order}"


# TODO: Add ProductVariant model for sizes/colors
# TODO: Add ProductReview model
# TODO: Add Wishlist model
