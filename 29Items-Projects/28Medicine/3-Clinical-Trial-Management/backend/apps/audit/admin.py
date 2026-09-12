from django.contrib import admin

from .models import AuditEvent


@admin.register(AuditEvent)
class AuditEventAdmin(admin.ModelAdmin):
    list_display = ("created_at", "actor_label", "action", "entity_type", "entity_id")
    list_filter = ("action", "entity_type")
    search_fields = ("entity_id", "correlation_id", "actor_label")
    readonly_fields = [f.name for f in AuditEvent._meta.fields]
    date_hierarchy = "created_at"

    # Append-only: no creation, change, or deletion through the admin.
    def has_add_permission(self, request) -> bool:
        return False

    def has_change_permission(self, request, obj=None) -> bool:
        return False

    def has_delete_permission(self, request, obj=None) -> bool:
        return False
