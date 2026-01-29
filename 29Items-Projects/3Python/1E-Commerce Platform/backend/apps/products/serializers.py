"""Product serializers for E-Commerce Platform."""
from rest_framework import serializers

from .models import Category, Product, ProductImage, ProductReview, ProductVariant


class CategorySerializer(serializers.ModelSerializer):
    """Serializer for product categories."""

    children = serializers.SerializerMethodField()
    full_path = serializers.CharField(read_only=True)

    class Meta:
        model = Category
        fields = [
            'id', 'name', 'slug', 'description', 'parent',
            'image', 'is_active', 'full_path', 'children'
        ]

    def get_children(self, obj):
        """Return nested child categories."""
        children = obj.children.filter(is_active=True)
        return CategorySerializer(children, many=True).data


class ProductImageSerializer(serializers.ModelSerializer):
    """Serializer for product images."""

    class Meta:
        model = ProductImage
        fields = ['id', 'image', 'alt_text', 'is_primary', 'sort_order']


class ProductVariantSerializer(serializers.ModelSerializer):
    """Serializer for product variants."""

    effective_price = serializers.DecimalField(
        max_digits=10,
        decimal_places=2,
        read_only=True
    )

    class Meta:
        model = ProductVariant
        fields = [
            'id', 'sku', 'name', 'price', 'effective_price',
            'attributes', 'is_active'
        ]


class ProductReviewSerializer(serializers.ModelSerializer):
    """Serializer for product reviews."""

    user_name = serializers.CharField(source='user.full_name', read_only=True)

    class Meta:
        model = ProductReview
        fields = [
            'id', 'user_name', 'rating', 'title', 'content',
            'is_verified_purchase', 'created_at'
        ]
        read_only_fields = ['is_verified_purchase']


class ProductListSerializer(serializers.ModelSerializer):
    """Lightweight serializer for product listings."""

    category_name = serializers.CharField(source='category.name', read_only=True)
    primary_image = serializers.SerializerMethodField()
    discount_percentage = serializers.IntegerField(read_only=True)
    avg_rating = serializers.SerializerMethodField()

    class Meta:
        model = Product
        fields = [
            'id', 'sku', 'name', 'slug', 'category_name',
            'price', 'compare_at_price', 'discount_percentage',
            'primary_image', 'avg_rating', 'is_featured'
        ]

    def get_primary_image(self, obj):
        """Return the primary product image URL."""
        primary = obj.images.filter(is_primary=True).first()
        if primary:
            return primary.image.url
        first_image = obj.images.first()
        return first_image.image.url if first_image else None

    def get_avg_rating(self, obj):
        """Return average rating from approved reviews."""
        reviews = obj.reviews.filter(is_approved=True)
        if reviews.exists():
            return round(reviews.aggregate(
                avg=models.Avg('rating')
            )['avg'], 1)
        return None


class ProductDetailSerializer(serializers.ModelSerializer):
    """Full serializer for product detail view."""

    category = CategorySerializer(read_only=True)
    images = ProductImageSerializer(many=True, read_only=True)
    variants = ProductVariantSerializer(many=True, read_only=True)
    reviews = ProductReviewSerializer(many=True, read_only=True)
    discount_percentage = serializers.IntegerField(read_only=True)
    vendor_name = serializers.CharField(source='vendor.business_name', read_only=True)

    class Meta:
        model = Product
        fields = [
            'id', 'sku', 'name', 'slug', 'category', 'vendor_name',
            'short_description', 'description',
            'price', 'compare_at_price', 'discount_percentage',
            'status', 'is_featured', 'weight', 'dimensions',
            'meta_title', 'meta_description',
            'images', 'variants', 'reviews',
            'created_at', 'updated_at'
        ]


class ProductCreateSerializer(serializers.ModelSerializer):
    """Serializer for creating/updating products (vendor use)."""

    class Meta:
        model = Product
        fields = [
            'name', 'slug', 'category', 'short_description', 'description',
            'price', 'compare_at_price', 'cost_price',
            'weight', 'dimensions', 'meta_title', 'meta_description'
        ]

    def create(self, validated_data):
        # Auto-generate SKU
        import uuid
        validated_data['sku'] = f"SKU-{uuid.uuid4().hex[:8].upper()}"
        validated_data['vendor'] = self.context['request'].user.vendor
        return super().create(validated_data)
