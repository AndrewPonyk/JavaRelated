from rest_framework import serializers

from .models import Vendor


class VendorSerializer(serializers.ModelSerializer):
    class Meta:
        model = Vendor
        fields = ["id", "name", "slug", "status", "created_at"]
        read_only_fields = ["id", "slug", "status", "created_at"]
