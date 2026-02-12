"""
API Serializers

Django REST Framework serializers for all models.
"""

from rest_framework import serializers
from backend.core.models import (
    User,
    Product,
    Category,
    Cart,
    CartItem,
    Order,
    OrderItem,
    Vendor,
    Wishlist,
    WishlistItem,
    RecentlyViewed,
    Review,
    ReviewVote,
    ReviewImage,
)


# =============================================================================
# User Serializers
# =============================================================================

class UserSerializer(serializers.ModelSerializer):
    """Serializer for User model."""

    full_name = serializers.CharField(source="get_full_name", read_only=True)

    class Meta:
        model = User
        fields = [
            "id",
            "email",
            "first_name",
            "last_name",
            "full_name",
            "phone",
            "avatar",
            "is_vendor",
            "is_verified",
            "created_at",
        ]
        read_only_fields = ["id", "is_verified", "created_at"]


class RegisterSerializer(serializers.ModelSerializer):
    """Serializer for user registration."""

    password = serializers.CharField(write_only=True, min_length=8)
    password_confirm = serializers.CharField(write_only=True, min_length=8)

    class Meta:
        model = User
        fields = [
            "email",
            "password",
            "password_confirm",
            "first_name",
            "last_name",
            "phone",
        ]

    def validate(self, attrs):
        if attrs["password"] != attrs["password_confirm"]:
            raise serializers.ValidationError("Passwords do not match")
        return attrs

    def create(self, validated_data):
        """Create user with hashed password."""
        validated_data.pop("password_confirm")  # Remove before creating user
        password = validated_data.pop("password")
        user = User.objects.create(**validated_data)
        user.set_password(password)
        user.save()
        return user


class LoginSerializer(serializers.Serializer):
    """Serializer for user login."""

    email = serializers.EmailField()
    password = serializers.CharField()

    def validate(self, attrs):
        """Validate credentials and return user."""
        from django.contrib.auth import authenticate

        email = attrs.get("email")
        password = attrs.get("password")

        user = authenticate(request=self.context.get("request"), username=email, password=password)

        if not user:
            raise serializers.ValidationError("Invalid email or password")

        if not user.is_active:
            raise serializers.ValidationError("User account is disabled")

        attrs["user"] = user
        return attrs


class ChangePasswordSerializer(serializers.Serializer):
    """Serializer for changing password."""

    old_password = serializers.CharField(write_only=True, required=True)
    new_password = serializers.CharField(write_only=True, required=True, min_length=8)
    confirm_password = serializers.CharField(write_only=True, required=True)

    def validate_old_password(self, value):
        """Validate old password."""
        user = self.context["request"].user
        if not user.check_password(value):
            raise serializers.ValidationError("Incorrect old password")
        return value

    def validate(self, attrs):
        """Validate password confirmation."""
        if attrs["new_password"] != attrs["confirm_password"]:
            raise serializers.ValidationError("New passwords do not match")
        return attrs

    def save(self):
        """Update user's password."""
        user = self.context["request"].user
        user.set_password(self.validated_data["new_password"])
        user.save()
        return user


# =============================================================================
# Product Serializers
# =============================================================================

class CategorySerializer(serializers.ModelSerializer):
    """Serializer for Category model."""

    product_count = serializers.IntegerField(read_only=True)

    class Meta:
        model = Category
        fields = ["id", "name", "slug", "description", "image", "parent", "product_count"]


class ProductSerializer(serializers.ModelSerializer):
    """Serializer for Product model (list view)."""

    category_name = serializers.CharField(source="category.name", read_only=True)
    vendor_name = serializers.CharField(source="vendor.name", read_only=True)
    discount_percentage = serializers.ReadOnlyField()

    class Meta:
        model = Product
        fields = [
            "id",
            "name",
            "slug",
            "short_description",
            "price",
            "original_price",
            "discount_percentage",
            "main_image",
            "category",
            "category_name",
            "vendor",
            "vendor_name",
            "stock",
            "average_rating",
            "review_count",
            "is_featured",
            "created_at",
        ]


class ProductDetailSerializer(ProductSerializer):
    """Serializer for Product model (detail view)."""

    images = serializers.SerializerMethodField()
    specifications = serializers.JSONField()

    class Meta(ProductSerializer.Meta):
        fields = ProductSerializer.Meta.fields + [
            "description",
            "images",
            "specifications",
            "meta_title",
            "meta_description",
        ]

    def get_images(self, obj):
        """Get all product images."""
        images = [obj.main_image.url if obj.main_image else ""]
        images.extend([
            img.image.url for img in obj.product_images.all()
        ])
        return images


# =============================================================================
# Cart Serializers
# =============================================================================

class CartItemSerializer(serializers.ModelSerializer):
    """Serializer for CartItem model."""

    product_name = serializers.CharField(source="product.name", read_only=True)
    product_slug = serializers.CharField(source="product.slug", read_only=True)
    product_image = serializers.ImageField(source="product.main_image", read_only=True)
    stock = serializers.IntegerField(source="product.stock", read_only=True)

    class Meta:
        model = CartItem
        fields = [
            "id",
            "product",
            "product_name",
            "product_slug",
            "product_image",
            "quantity",
            "unit_price",
            "subtotal",
            "stock",
        ]


class CartSerializer(serializers.ModelSerializer):
    """Serializer for Cart model."""

    items = CartItemSerializer(many=True, read_only=True)
    total_items = serializers.ReadOnlyField()
    total_price = serializers.ReadOnlyField()

    class Meta:
        model = Cart
        fields = ["id", "items", "total_items", "total_price", "created_at", "updated_at"]


# =============================================================================
# Order Serializers
# =============================================================================

class OrderItemSerializer(serializers.ModelSerializer):
    """Serializer for OrderItem model."""

    class Meta:
        model = OrderItem
        fields = [
            "id",
            "product",
            "product_name",
            "product_slug",
            "product_image",
            "unit_price",
            "quantity",
            "subtotal",
        ]


class OrderSerializer(serializers.ModelSerializer):
    """Serializer for Order model (list view)."""

    class Meta:
        model = Order
        fields = [
            "id",
            "order_number",
            "status",
            "subtotal",
            "total",
            "created_at",
        ]


class OrderDetailSerializer(OrderSerializer):
    """Serializer for Order model (detail view)."""

    items = OrderItemSerializer(many=True, read_only=True)

    class Meta(OrderSerializer.Meta):
        fields = OrderSerializer.Meta.fields + [
            "shipping_address",
            "billing_address",
            "payment_method",
            "payment_status",
            "shipping_method",
            "tracking_number",
            "carrier",
            "customer_notes",
            "items",
        ]


# =============================================================================
# Vendor Serializers
# =============================================================================

class VendorSerializer(serializers.ModelSerializer):
    """Serializer for Vendor model."""

    class Meta:
        model = Vendor
        fields = [
            "id",
            "name",
            "slug",
            "description",
            "logo",
            "is_active",
            "is_verified",
        ]


# =============================================================================
# Wishlist Serializers
# =============================================================================

class WishlistItemSerializer(serializers.ModelSerializer):
    """Serializer for WishlistItem model."""

    product_name = serializers.CharField(source="product.name", read_only=True)
    product_slug = serializers.CharField(source="product.slug", read_only=True)
    product_image = serializers.ImageField(source="product.main_image", read_only=True)
    product_price = serializers.DecimalField(
        source="product.price",
        max_digits=10,
        decimal_places=2,
        read_only=True,
    )
    product_original_price = serializers.DecimalField(
        source="product.original_price",
        max_digits=10,
        decimal_places=2,
        read_only=True,
    )
    in_stock = serializers.BooleanField(source="is_in_stock", read_only=True)
    on_sale = serializers.BooleanField(source="is_on_sale", read_only=True)

    class Meta:
        model = WishlistItem
        fields = [
            "id",
            "product",
            "product_name",
            "product_slug",
            "product_image",
            "product_price",
            "product_original_price",
            "in_stock",
            "on_sale",
            "notes",
            "priority",
            "created_at",
        ]


class WishlistSerializer(serializers.ModelSerializer):
    """Serializer for Wishlist model."""

    items = WishlistItemSerializer(many=True, read_only=True)
    item_count = serializers.ReadOnlyField()

    class Meta:
        model = Wishlist
        fields = [
            "id",
            "name",
            "user",
            "is_default",
            "item_count",
            "created_at",
            "updated_at",
            "items",
        ]
        read_only_fields = ["user"]


class RecentlyViewedSerializer(serializers.ModelSerializer):
    """Serializer for RecentlyViewed model."""

    product_name = serializers.CharField(source="product.name", read_only=True)
    product_slug = serializers.CharField(source="product.slug", read_only=True)
    product_image = serializers.ImageField(source="product.main_image", read_only=True)
    product_price = serializers.DecimalField(
        source="product.price",
        max_digits=10,
        decimal_places=2,
        read_only=True,
    )

    class Meta:
        model = RecentlyViewed
        fields = [
            "id",
            "product",
            "product_name",
            "product_slug",
            "product_image",
            "product_price",
            "viewed_at",
        ]


# =============================================================================
# Review Serializers
# =============================================================================

class ReviewImageSerializer(serializers.ModelSerializer):
    """Serializer for ReviewImage model."""

    class Meta:
        model = ReviewImage
        fields = ["id", "image", "caption"]


class ReviewSerializer(serializers.ModelSerializer):
    """Serializer for Review model."""

    user_name = serializers.CharField(source="user.get_full_name", read_only=True)
    user_avatar = serializers.ImageField(source="user.avatar", read_only=True)
    product_name = serializers.CharField(source="product.name", read_only=True)
    product_slug = serializers.CharField(source="product.slug", read_only=True)
    can_edit = serializers.BooleanField(read_only=True)

    class Meta:
        model = Review
        fields = [
            "id",
            "user",
            "user_name",
            "user_avatar",
            "product",
            "product_name",
            "product_slug",
            "order",
            "rating",
            "title",
            "comment",
            "helpful_count",
            "not_helpful_count",
            "is_verified_purchase",
            "is_approved",
            "is_flagged",
            "admin_response",
            "admin_response_date",
            "created_at",
            "updated_at",
            "can_edit",
        ]
        read_only_fields = ["user", "helpful_count", "not_helpful_count", "is_verified_purchase"]

    def create(self, validated_data):
        """Create review and mark as verified if from order."""
        from backend.core.models import Order

        order = validated_data.get("order")
        if order:
            validated_data["is_verified_purchase"] = True

        return super().create(validated_data)


class ReviewDetailSerializer(ReviewSerializer):
    """Detailed serializer for Review model."""

    images = ReviewImageSerializer(many=True, read_only=True)

    class Meta(ReviewSerializer.Meta):
        fields = ReviewSerializer.Meta.fields + ["images"]


class ReviewCreateSerializer(serializers.ModelSerializer):
    """Serializer for creating a review."""

    class Meta:
        model = Review
        fields = [
            "product",
            "order",
            "rating",
            "title",
            "comment",
        ]

    def validate(self, attrs):
        """Validate review can be created."""
        user = self.context["request"].user
        product = attrs["product"]
        order = attrs.get("order")

        # Check if user already reviewed this product
        if Review.objects.filter(user=user, product=product).exists():
            raise serializers.ValidationError("You have already reviewed this product")

        # If order is provided, validate it belongs to user and is delivered
        if order:
            if order.user != user:
                raise serializers.ValidationError("Order does not belong to you")
            if order.status not in ["delivered", "completed"]:
                raise serializers.ValidationError("Reviews can only be created for delivered orders")

            # Check if product is in order
            if not order.items.filter(product=product).exists():
                raise serializers.ValidationError("Product not found in this order")

        return attrs
