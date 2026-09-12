from django.contrib import admin

from .models import Deviation, Enrollment, Visit


@admin.register(Enrollment)
class EnrollmentAdmin(admin.ModelAdmin):
    list_display = ("subject", "study", "arm", "status", "baseline_date")
    list_filter = ("status", "study")
    raw_id_fields = ("subject", "study", "arm")


@admin.register(Visit)
class VisitAdmin(admin.ModelAdmin):
    list_display = ("enrollment", "template", "scheduled_date", "actual_date", "status")
    list_filter = ("status",)


@admin.register(Deviation)
class DeviationAdmin(admin.ModelAdmin):
    list_display = ("enrollment", "template", "kind", "resolved", "created_at")
    list_filter = ("kind", "resolved")
