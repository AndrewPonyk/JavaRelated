"""
Product Validators

Request validators using DRF serializers or Pydantic.
"""

from rest_framework import serializers


class ProductValidator:
    """Validators for product-related requests."""

    class ProductCreate(serializers.Serializer):
        """Validator for creating a new product."""

        name = serializers.CharField(max_length=255, required=True)
        slug = serializers.SlugField(max_length=255, required=True)
        description = serializers.TextField(required=True)
        short_description = serializers.CharField(max_length=500, required=False, allow_blank=True)
        price = serializers.DecimalField(max_digits=10, decimal_places=2, required=True, min_value=0)
        original_price = serializers.DecimalField(
            max_digits=10,
            decimal_places=2,
            required=False,
            allow_null=True,
            min_value=0,
        )
        category = serializers.IntegerField(required=True)
        vendor = serializers.IntegerField(required=True)
        stock = serializers.IntegerField(required=True, min_value=0)
        is_active = serializers.BooleanField(required=False, default=True)
        is_featured = serializers.BooleanField(required=False, default=False)
        specifications = serializers.JSONField(required=False, default=dict)
        meta_title = serializers.CharField(max_length=255, required=False, allow_blank=True)
        meta_description = serializers.CharField(required=False, allow_blank=True)
        meta_keywords = serializers.CharField(max_length=500, required=False, allow_blank=True)

        def validate(self, attrs):
            """Validate product data."""
            # Validate original_price is greater than price if set
            if attrs.get("original_price") and attrs["original_price"] <= attrs["price"]:
                raise serializers.ValidationError(
                    "original_price must be greater than price"
                )
            return attrs

    class ProductUpdate(serializers.Serializer):
        """Validator for updating a product."""

        name = serializers.CharField(max_length=255, required=False)
        slug = serializers.SlugField(max_length=255, required=False)
        description = serializers.TextField(required=False)
        short_description = serializers.CharField(max_length=500, required=False, allow_blank=True)
        price = serializers.DecimalField(max_digits=10, decimal_places=2, required=False, min_value=0)
        original_price = serializers.DecimalField(
            max_digits=10,
            decimal_places=2,
            required=False,
            allow_null=True,
            min_value=0,
        )
        category = serializers.IntegerField(required=False)
        stock = serializers.IntegerField(required=False, min_value=0)
        is_active = serializers.BooleanField(required=False)
        is_featured = serializers.BooleanField(required=False)
        specifications = serializers.JSONField(required=False)
        meta_title = serializers.CharField(max_length=255, required=False, allow_blank=True)
        meta_description = serializers.CharField(required=False, allow_blank=True)
        meta_keywords = serializers.CharField(max_length=500, required=False, allow_blank=True)

        def validate(self, attrs):
            """Validate product update data."""
            price = attrs.get("price")
            original_price = attrs.get("original_price")

            if price and original_price and original_price <= price:
                raise serializers.ValidationError(
                    "original_price must be greater than price"
                )
            return attrs


class CategoryValidator:
    """Validators for category-related requests."""

    class CategoryCreate(serializers.Serializer):
        """Validator for creating a new category."""

        name = serializers.CharField(max_length=200, required=True)
        slug = serializers.SlugField(max_length=255, required=True)
        description = serializers.TextField(required=False, allow_blank=True)
        parent = serializers.IntegerField(required=False, allow_null=True)
        is_active = serializers.BooleanField(required=False, default=True)

    class CategoryUpdate(serializers.Serializer):
        """Validator for updating a category."""

        name = serializers.CharField(max_length=200, required=False)
        slug = serializers.SlugField(max_length=255, required=False)
        description = serializers.TextField(required=False, allow_blank=True)
        parent = serializers.IntegerField(required=False, allow_null=True)
        is_active = serializers.BooleanField(required=False)
