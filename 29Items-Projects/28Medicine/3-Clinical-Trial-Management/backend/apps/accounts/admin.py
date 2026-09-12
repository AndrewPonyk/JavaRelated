from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as DjangoUserAdmin

from .models import ElectronicSignature, User


@admin.register(User)
class UserAdmin(DjangoUserAdmin):
    list_display = ("username", "email", "role", "mfa_enrolled", "is_active", "is_staff")
    list_filter = ("role", "mfa_enrolled", "is_active", "is_staff")
    fieldsets = tuple(DjangoUserAdmin.fieldsets or ()) + (
        ("Clinical role", {"fields": ("role", "mfa_enrolled")}),
    )


@admin.register(ElectronicSignature)
class ElectronicSignatureAdmin(admin.ModelAdmin):
    list_display = ("signer", "meaning", "entity_type", "entity_id", "signed_at")
    list_filter = ("meaning",)
    search_fields = ("entity_type", "entity_id")
    readonly_fields = (
        "signer",
        "meaning",
        "entity_type",
        "entity_id",
        "signed_at",
        "signature_hash",
    )

    def has_change_permission(self, request, obj=None) -> bool:
        return False  # signatures are immutable

    def has_delete_permission(self, request, obj=None) -> bool:
        return False
