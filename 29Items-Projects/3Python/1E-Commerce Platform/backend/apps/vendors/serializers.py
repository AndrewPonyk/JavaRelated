"""Vendor serializers for E-Commerce Platform."""
from rest_framework import serializers

from .models import Vendor, VendorPayout


class VendorSerializer(serializers.ModelSerializer):
    """Serializer for vendor profiles."""

    owner_email = serializers.CharField(source='user.email', read_only=True)

    class Meta:
        model = Vendor
        fields = [
            'id', 'business_name', 'slug', 'description',
            'logo', 'banner', 'business_email', 'business_phone',
            'website', 'status', 'is_verified',
            'total_products', 'total_orders', 'avg_rating',
            'owner_email', 'created_at'
        ]
        read_only_fields = [
            'slug', 'status', 'is_verified',
            'total_products', 'total_orders', 'avg_rating'
        ]


class VendorPublicSerializer(serializers.ModelSerializer):
    """Public serializer for vendor store pages."""

    class Meta:
        model = Vendor
        fields = [
            'id', 'business_name', 'slug', 'description',
            'logo', 'banner', 'total_products', 'avg_rating'
        ]


class VendorRegistrationSerializer(serializers.ModelSerializer):
    """Serializer for vendor registration."""

    class Meta:
        model = Vendor
        fields = [
            'business_name', 'description', 'business_email',
            'business_phone', 'website', 'address',
            'city', 'state', 'postal_code', 'country'
        ]

    def create(self, validated_data):
        from django.utils.text import slugify
        user = self.context['request'].user

        # Generate unique slug
        base_slug = slugify(validated_data['business_name'])
        slug = base_slug
        counter = 1
        while Vendor.objects.filter(slug=slug).exists():
            slug = f"{base_slug}-{counter}"
            counter += 1

        validated_data['slug'] = slug
        validated_data['user'] = user

        # Update user as vendor
        user.is_vendor = True
        user.save()

        return super().create(validated_data)


class VendorDashboardSerializer(serializers.ModelSerializer):
    """Serializer for vendor dashboard data."""

    recent_orders_count = serializers.SerializerMethodField()
    pending_payouts = serializers.SerializerMethodField()

    class Meta:
        model = Vendor
        fields = [
            'id', 'business_name', 'status',
            'total_products', 'total_orders', 'total_revenue',
            'avg_rating', 'commission_rate',
            'recent_orders_count', 'pending_payouts'
        ]

    def get_recent_orders_count(self, obj):
        # TODO: Implement recent orders count (last 30 days)
        return 0

    def get_pending_payouts(self, obj):
        return obj.payouts.filter(status=VendorPayout.Status.PENDING).count()


class VendorPayoutSerializer(serializers.ModelSerializer):
    """Serializer for vendor payouts."""

    class Meta:
        model = VendorPayout
        fields = [
            'id', 'amount', 'commission_deducted', 'net_amount',
            'status', 'period_start', 'period_end',
            'payment_method', 'transaction_id',
            'created_at', 'processed_at'
        ]
