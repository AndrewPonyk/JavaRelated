"""
Django Admin Configuration

Register models for Django admin interface.
"""

from django.contrib import admin
from django.utils.html import format_html

from backend.core.models import (
    User,
    Product,
    ProductImage,
    Category,
    Vendor,
    Cart,
    CartItem,
    Order,
    OrderItem,
    Wishlist,
    WishlistItem,
    RecentlyViewed,
    Review,
    ReviewVote,
    ReviewImage,
)


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    """Admin interface for User model."""

    list_display = ["email", "first_name", "last_name", "is_vendor", "is_verified", "is_active", "created_at"]
    list_filter = ["is_vendor", "is_verified", "is_active", "is_staff"]
    search_fields = ["email", "first_name", "last_name"]
    readonly_fields = ["created_at", "updated_at"]


@admin.register(Vendor)
class VendorAdmin(admin.ModelAdmin):
    """Admin interface for Vendor model."""

    list_display = ["name", "slug", "email", "is_active", "is_verified", "created_at"]
    list_filter = ["is_active", "is_verified", "business_type"]
    search_fields = ["name", "email", "business_name"]
    prepopulated_fields = {"slug": ("name",)}
    readonly_fields = ["created_at", "updated_at"]


@admin.register(Category)
class CategoryAdmin(admin.ModelAdmin):
    """Admin interface for Category model."""

    list_display = ["name", "slug", "parent", "is_active", "product_count"]
    list_filter = ["is_active"]
    search_fields = ["name", "description"]
    prepopulated_fields = {"slug": ("name",)}


class ProductImageInline(admin.TabularInline):
    """Inline admin for ProductImage."""

    model = ProductImage
    extra = 1
    readonly_fields = ["image_preview"]

    def image_preview(self, obj):
        """Show image preview."""
        if obj.image:
            return format_html('<img src="{}" width="100" />', obj.image.url)
        return ""


@admin.register(Product)
class ProductAdmin(admin.ModelAdmin):
    """Admin interface for Product model."""

    list_display = ["name", "slug", "category", "vendor", "price", "stock", "is_active", "is_featured", "created_at"]
    list_filter = ["is_active", "is_featured", "category", "vendor"]
    search_fields = ["name", "description", "short_description"]
    prepopulated_fields = {"slug": ("name",)}
    readonly_fields = ["created_at", "updated_at", "average_rating", "review_count"]
    inlines = [ProductImageInline]


@admin.register(Cart)
class CartAdmin(admin.ModelAdmin):
    """Admin interface for Cart model."""

    list_display = ["id", "user", "total_items", "total_price", "created_at"]
    readonly_fields = ["created_at", "updated_at"]


@admin.register(CartItem)
class CartItemAdmin(admin.ModelAdmin):
    """Admin interface for CartItem model."""

    list_display = ["id", "cart", "product", "quantity", "unit_price", "subtotal"]
    list_filter = ["cart"]


@admin.register(Order)
class OrderAdmin(admin.ModelAdmin):
    """Admin interface for Order model."""

    list_display = ["order_number", "user", "status", "total", "created_at"]
    list_filter = ["status", "payment_status"]
    search_fields = ["order_number", "user__email"]
    readonly_fields = ["created_at", "updated_at"]


@admin.register(OrderItem)
class OrderItemAdmin(admin.ModelAdmin):
    """Admin interface for OrderItem model."""

    list_display = ["id", "order", "product", "quantity", "unit_price", "subtotal"]
    list_filter = ["order"]


@admin.register(Wishlist)
class WishlistAdmin(admin.ModelAdmin):
    """Admin interface for Wishlist model."""

    list_display = ["id", "name", "user", "item_count", "is_default"]
    list_filter = ["is_default"]


@admin.register(WishlistItem)
class WishlistItemAdmin(admin.ModelAdmin):
    """Admin interface for WishlistItem model."""

    list_display = ["id", "wishlist", "product", "priority", "created_at"]


@admin.register(RecentlyViewed)
class RecentlyViewedAdmin(admin.ModelAdmin):
    """Admin interface for RecentlyViewed model."""

    list_display = ["id", "user", "product", "viewed_at"]
    list_filter = ["viewed_at"]


class ReviewImageInline(admin.TabularInline):
    """Inline admin for ReviewImage."""

    model = ReviewImage
    extra = 1


@admin.register(Review)
class ReviewAdmin(admin.ModelAdmin):
    """Admin interface for Review model."""

    list_display = ["id", "product", "user", "rating", "is_verified_purchase", "is_approved", "created_at"]
    list_filter = ["rating", "is_verified_purchase", "is_approved", "is_flagged"]
    search_fields = ["title", "comment", "user__email", "product__name"]
    readonly_fields = ["created_at", "updated_at", "helpful_count", "not_helpful_count"]
    inlines = [ReviewImageInline]


@admin.register(ReviewVote)
class ReviewVoteAdmin(admin.ModelAdmin):
    """Admin interface for ReviewVote model."""

    list_display = ["id", "review", "user", "is_helpful"]
    list_filter = ["is_helpful"]
