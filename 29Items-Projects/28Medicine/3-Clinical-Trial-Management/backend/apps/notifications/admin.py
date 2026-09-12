from django.contrib import admin

from .models import Notification


@admin.register(Notification)
class NotificationAdmin(admin.ModelAdmin):
    list_display = ("recipient", "kind", "subject", "read", "created_at")
    list_filter = ("kind", "read")
    search_fields = ("subject", "dedupe_key")
