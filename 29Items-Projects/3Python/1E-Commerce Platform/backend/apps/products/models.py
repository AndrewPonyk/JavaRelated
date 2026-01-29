"""Product models for E-Commerce Platform."""
import uuid
from decimal import Decimal

from django.conf import settings
from django.core.validators import MinValueValidator
from django.db import models


# |su:11 Self-referential FK creates a tree structure (parent-child)
# Common pattern for categories, comments, org charts
class Category(models.Model):
    """Product category model with hierarchical support."""

    name = models.CharField(max_length=100)
    slug = models.SlugField(max_length=100, unique=True)
    description = models.TextField(blank=True)
    # |su:12 ForeignKey('self') creates parent-child relationship within same table
    # null=True allows root categories (no parent), related_name='children' for reverse access
    parent = models.ForeignKey(
        'self',
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        related_name='children'
    )
    image = models.ImageField(upload_to='categories/', blank=True, null=True)
    is_active = models.BooleanField(default=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'category'
        verbose_name_plural = 'categories'
        ordering = ['name']

    def __str__(self):
        return self.name

    @property
    def full_path(self):
        """Return the full category path (e.g., 'Electronics > Phones > Smartphones')."""
        if self.parent:
            return f'{self.parent.full_path} > {self.name}'
        return self.name


class Product(models.Model):
    """Main product model."""

    class Status(models.TextChoices):
        DRAFT = 'draft', 'Draft'
        PENDING = 'pending', 'Pending Review'
        ACTIVE = 'active', 'Active'
        INACTIVE = 'inactive', 'Inactive'

    # |su:13 UUID primary key instead of auto-increment integer:
    # - Globally unique (safe for distributed systems, merging DBs)
    # - Non-guessable (can't enumerate /products/1, /products/2...)
    # - Downside: larger, slower joins than integers
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    sku = models.CharField(max_length=50, unique=True)
    name = models.CharField(max_length=255)
    slug = models.SlugField(max_length=255, unique=True)

    # Relationships
    # |su:14 on_delete options:
    # - CASCADE: delete products when category deleted (dangerous here!)
    # - PROTECT: prevent category deletion if products exist (safe)
    # - SET_NULL: set to null (requires null=True)
    # - SET_DEFAULT: set to default value
    category = models.ForeignKey(
        Category,
        on_delete=models.PROTECT,
        related_name='products'
    )
    vendor = models.ForeignKey(
        'vendors.Vendor',
        on_delete=models.CASCADE,
        related_name='products'
    )

    # Description
    short_description = models.CharField(max_length=500)
    description = models.TextField()

    # Pricing
    price = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        validators=[MinValueValidator(Decimal('0.01'))]
    )
    compare_at_price = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        null=True,
        blank=True,
        validators=[MinValueValidator(Decimal('0.01'))]
    )
    cost_price = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        null=True,
        blank=True,
        validators=[MinValueValidator(Decimal('0.01'))]
    )

    # Status
    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.DRAFT
    )
    is_featured = models.BooleanField(default=False)

    # Attributes
    weight = models.DecimalField(max_digits=8, decimal_places=2, null=True, blank=True)
    dimensions = models.JSONField(default=dict, blank=True)  # {length, width, height}

    # SEO
    meta_title = models.CharField(max_length=100, blank=True)
    meta_description = models.CharField(max_length=300, blank=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'product'
        verbose_name_plural = 'products'
        ordering = ['-created_at']
        # |su:15 Database indexes speed up queries but slow down writes
        # Add indexes for fields used in WHERE, ORDER BY, JOIN clauses
        # Composite indexes (multiple fields) optimize queries filtering by both
        indexes = [
            models.Index(fields=['slug']),
            models.Index(fields=['sku']),
            models.Index(fields=['status', 'is_featured']),  # For "featured active products" query
            models.Index(fields=['category', 'status']),     # For "products in category" query
        ]

    def __str__(self):
        return self.name

    @property
    def discount_percentage(self):
        """Calculate discount percentage if compare_at_price is set."""
        if self.compare_at_price and self.compare_at_price > self.price:
            return round((1 - self.price / self.compare_at_price) * 100)
        return 0


class ProductImage(models.Model):
    """Product image model."""

    product = models.ForeignKey(
        Product,
        on_delete=models.CASCADE,
        related_name='images'
    )
    image = models.ImageField(upload_to='products/')
    alt_text = models.CharField(max_length=200, blank=True)
    is_primary = models.BooleanField(default=False)
    sort_order = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ['sort_order']

    def save(self, *args, **kwargs):
        """Ensure only one primary image per product."""
        if self.is_primary:
            ProductImage.objects.filter(
                product=self.product,
                is_primary=True
            ).update(is_primary=False)
        super().save(*args, **kwargs)


class ProductVariant(models.Model):
    """Product variant model for size, color, etc."""

    product = models.ForeignKey(
        Product,
        on_delete=models.CASCADE,
        related_name='variants'
    )
    sku = models.CharField(max_length=50, unique=True)
    name = models.CharField(max_length=100)  # e.g., "Large / Red"

    # Variant-specific pricing (optional, falls back to product price)
    price = models.DecimalField(
        max_digits=10,
        decimal_places=2,
        null=True,
        blank=True
    )

    # Attributes
    attributes = models.JSONField(default=dict)  # {size: "L", color: "red"}

    # Inventory tracked in inventory app
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ['name']

    def __str__(self):
        return f'{self.product.name} - {self.name}'

    @property
    def effective_price(self):
        """Return variant price or fall back to product price."""
        return self.price if self.price else self.product.price


class ProductReview(models.Model):
    """Product review and rating model."""

    product = models.ForeignKey(
        Product,
        on_delete=models.CASCADE,
        related_name='reviews'
    )
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='reviews'
    )

    rating = models.PositiveSmallIntegerField(
        validators=[MinValueValidator(1)]
    )  # 1-5
    title = models.CharField(max_length=200)
    content = models.TextField()

    is_verified_purchase = models.BooleanField(default=False)
    is_approved = models.BooleanField(default=False)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']
        unique_together = ['product', 'user']

    def __str__(self):
        return f'{self.product.name} - {self.rating} stars'
