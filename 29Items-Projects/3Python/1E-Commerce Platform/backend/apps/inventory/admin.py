"""Admin configuration for inventory app."""
from django.contrib import admin
from .models import InventoryItem, InventoryTransaction, StockAlert


@admin.register(InventoryItem)
class InventoryItemAdmin(admin.ModelAdmin):
    list_display = ['product', 'variant', 'quantity_on_hand', 'quantity_reserved', 'quantity_available', 'is_low_stock']
    list_filter = ['product__category']
    search_fields = ['product__name', 'variant__name']
    readonly_fields = ['quantity_available', 'updated_at']
    raw_id_fields = ['product', 'variant']


@admin.register(InventoryTransaction)
class InventoryTransactionAdmin(admin.ModelAdmin):
    list_display = ['inventory_item', 'transaction_type', 'quantity', 'created_at', 'created_by']
    list_filter = ['transaction_type', 'created_at']
    search_fields = ['inventory_item__product__name', 'reference_id']
    readonly_fields = ['created_at']


@admin.register(StockAlert)
class StockAlertAdmin(admin.ModelAdmin):
    list_display = ['inventory_item', 'status', 'threshold_triggered', 'current_quantity', 'created_at']
    list_filter = ['status', 'created_at']
    readonly_fields = ['created_at', 'acknowledged_at', 'resolved_at']
