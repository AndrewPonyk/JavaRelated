from django.contrib import admin

from .models import Subject


@admin.register(Subject)
class SubjectAdmin(admin.ModelAdmin):
    # Never list raw PHI columns; the de-identified code is the handle.
    list_display = ("subject_code", "sex_at_birth", "enrolled", "created_at")
    search_fields = ("subject_code",)
    list_filter = ("enrolled", "sex_at_birth")
    readonly_fields = ("public_id", "created_at", "updated_at")
