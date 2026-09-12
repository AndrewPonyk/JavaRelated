from django.contrib import admin

from .models import Vendor


@admin.register(Vendor)
class VendorAdmin(admin.ModelAdmin):
    list_display = ("name", "slug", "status", "owner", "created_at")
    list_filter = ("status",)
    search_fields = ("name", "slug", "owner__email")
    prepopulated_fields = {"slug": ("name",)}
