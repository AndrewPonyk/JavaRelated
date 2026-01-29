"""Admin configuration for vendors app."""
from django.contrib import admin
from .models import Vendor, VendorPayout


@admin.register(Vendor)
class VendorAdmin(admin.ModelAdmin):
    list_display = ['business_name', 'user', 'status', 'is_verified', 'commission_rate', 'created_at']
    list_filter = ['status', 'is_verified']
    search_fields = ['business_name', 'user__email', 'slug']
    prepopulated_fields = {'slug': ('business_name',)}
    readonly_fields = ['total_products', 'total_orders', 'total_revenue', 'avg_rating', 'created_at', 'updated_at']


@admin.register(VendorPayout)
class VendorPayoutAdmin(admin.ModelAdmin):
    list_display = ['vendor', 'amount', 'net_amount', 'status', 'period_start', 'period_end']
    list_filter = ['status']
    search_fields = ['vendor__business_name']
