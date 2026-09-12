from django.contrib import admin

from .models import Screening


@admin.register(Screening)
class ScreeningAdmin(admin.ModelAdmin):
    list_display = (
        "subject",
        "study",
        "status",
        "ml_score",
        "ml_recommendation",
        "human_decision",
    )
    list_filter = ("status", "ml_recommendation", "human_decision")
    raw_id_fields = ("study", "subject", "decided_by")
    # note_text holds PHI — keep it out of the changelist; redact-by-omission.
    readonly_fields = ("rationale", "model_version", "ontology_versions", "decided_at")
