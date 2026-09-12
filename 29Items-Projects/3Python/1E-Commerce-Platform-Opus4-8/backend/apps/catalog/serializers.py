"""DRF serializers — the input-validation boundary (Deliverable 4.2)."""

from __future__ import annotations

from decimal import Decimal

from rest_framework import serializers

from .models import Category, Product


class CategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = Category
        fields = ["id", "name", "slug", "parent"]
        read_only_fields = ["slug"]


class VendorBriefSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    name = serializers.CharField()
    slug = serializers.CharField()


class ProductSerializer(serializers.ModelSerializer):
    category = CategorySerializer(read_only=True)
    category_id = serializers.PrimaryKeyRelatedField(
        source="category", queryset=Category.objects.all(), write_only=True
    )
    vendor = VendorBriefSerializer(read_only=True)
    quantity_available = serializers.SerializerMethodField()

    class Meta:
        model = Product
        fields = [
            "id",
            "sku",
            "name",
            "slug",
            "description",
            "price",
            "currency",
            "status",
            "category",
            "category_id",
            "vendor",
            "is_purchasable",
            "quantity_available",
            "created_at",
        ]
        read_only_fields = ["slug", "is_purchasable", "vendor", "created_at"]

    def get_quantity_available(self, obj: Product) -> int:
        stock = getattr(obj, "stock", None)
        return stock.quantity_available if stock else 0

    def validate_price(self, value: Decimal) -> Decimal:
        if value < Decimal("0"):
            raise serializers.ValidationError("Price must not be negative.")
        return value

    def validate_sku(self, value: str) -> str:
        return value.strip().upper()
