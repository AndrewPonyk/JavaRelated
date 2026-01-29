"""User serializers for E-Commerce Platform."""
from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

from .models import Address, WishlistItem

User = get_user_model()


class CustomTokenObtainPairSerializer(TokenObtainPairSerializer):
    """Custom JWT token serializer that includes user data."""

    @classmethod
    def get_token(cls, user):
        token = super().get_token(user)
        # Add custom claims
        token['email'] = user.email
        token['is_vendor'] = user.is_vendor
        return token


class UserRegistrationSerializer(serializers.ModelSerializer):
    """Serializer for user registration."""

    password = serializers.CharField(
        write_only=True,
        required=True,
        validators=[validate_password],
        style={'input_type': 'password'}
    )
    password_confirm = serializers.CharField(
        write_only=True,
        required=True,
        style={'input_type': 'password'}
    )

    class Meta:
        model = User
        fields = [
            'email', 'password', 'password_confirm',
            'first_name', 'last_name', 'phone_number'
        ]

    def validate(self, attrs):
        if attrs['password'] != attrs['password_confirm']:
            raise serializers.ValidationError(
                {'password_confirm': "Passwords don't match."}
            )
        return attrs

    def create(self, validated_data):
        validated_data.pop('password_confirm')
        user = User.objects.create_user(**validated_data)
        return user


class UserSerializer(serializers.ModelSerializer):
    """Serializer for user profile."""

    full_name = serializers.CharField(read_only=True)

    class Meta:
        model = User
        fields = [
            'id', 'email', 'first_name', 'last_name', 'full_name',
            'phone_number', 'avatar', 'is_verified', 'is_vendor',
            'created_at', 'updated_at'
        ]
        read_only_fields = ['id', 'email', 'is_verified', 'is_vendor', 'created_at', 'updated_at']


class AddressSerializer(serializers.ModelSerializer):
    """Serializer for user addresses."""

    class Meta:
        model = Address
        fields = [
            'id', 'address_type', 'is_default',
            'street_address', 'apartment', 'city',
            'state', 'postal_code', 'country',
            'created_at', 'updated_at'
        ]
        read_only_fields = ['id', 'created_at', 'updated_at']


class PasswordChangeSerializer(serializers.Serializer):
    """Serializer for password change."""

    old_password = serializers.CharField(required=True)
    new_password = serializers.CharField(required=True, validators=[validate_password])

    def validate_old_password(self, value):
        user = self.context['request'].user
        if not user.check_password(value):
            raise serializers.ValidationError('Old password is incorrect.')
        return value


class WishlistItemSerializer(serializers.ModelSerializer):
    """Serializer for wishlist items."""

    product_id = serializers.UUIDField(write_only=True)
    product_name = serializers.CharField(source='product.name', read_only=True)
    product_slug = serializers.CharField(source='product.slug', read_only=True)
    product_price = serializers.DecimalField(
        source='product.price', max_digits=10, decimal_places=2, read_only=True
    )
    product_image = serializers.SerializerMethodField()

    class Meta:
        model = WishlistItem
        fields = [
            'id', 'product_id', 'product_name', 'product_slug',
            'product_price', 'product_image', 'created_at'
        ]
        read_only_fields = ['id', 'created_at']

    def get_product_image(self, obj):
        """Get the primary product image URL."""
        primary_image = obj.product.images.filter(is_primary=True).first()
        if primary_image:
            return primary_image.image.url
        first_image = obj.product.images.first()
        if first_image:
            return first_image.image.url
        return None

    def create(self, validated_data):
        product_id = validated_data.pop('product_id')
        from apps.products.models import Product
        product = Product.objects.get(id=product_id)
        return WishlistItem.objects.create(product=product, **validated_data)
