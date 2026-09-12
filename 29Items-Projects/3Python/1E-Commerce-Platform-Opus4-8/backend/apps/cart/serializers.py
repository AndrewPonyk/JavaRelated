from rest_framework import serializers

from apps.catalog.models import Product

from .models import Cart, CartItem


class CartItemSerializer(serializers.ModelSerializer):
    product_name = serializers.CharField(source="product.name", read_only=True)
    line_total = serializers.DecimalField(max_digits=12, decimal_places=2, read_only=True)

    class Meta:
        model = CartItem
        fields = ["id", "product", "product_name", "quantity", "unit_price", "line_total"]
        read_only_fields = ["id", "unit_price", "line_total", "product_name"]


class CartSerializer(serializers.ModelSerializer):
    items = CartItemSerializer(many=True, read_only=True)
    total = serializers.DecimalField(max_digits=12, decimal_places=2, read_only=True)
    item_count = serializers.SerializerMethodField()

    class Meta:
        model = Cart
        fields = ["id", "items", "total", "item_count", "updated_at"]

    def get_item_count(self, obj: Cart) -> int:
        return sum(item.quantity for item in obj.items.all())


class AddItemSerializer(serializers.Serializer):
    product_id = serializers.IntegerField()
    quantity = serializers.IntegerField(min_value=1, default=1)

    def validate_product_id(self, value: int) -> int:
        if not Product.objects.filter(id=value).exists():
            raise serializers.ValidationError("Product does not exist.")
        return value


class UpdateItemSerializer(serializers.Serializer):
    quantity = serializers.IntegerField(min_value=1)
