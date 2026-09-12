from django.contrib import admin

from .models import Arm, EligibilityCriterion, Protocol, Study, VisitTemplate


class EligibilityCriterionInline(admin.TabularInline):
    model = EligibilityCriterion
    extra = 0


class ArmInline(admin.TabularInline):
    model = Arm
    extra = 0


class VisitTemplateInline(admin.TabularInline):
    model = VisitTemplate
    extra = 0


@admin.register(Protocol)
class ProtocolAdmin(admin.ModelAdmin):
    list_display = ("code", "version", "title", "phase", "effective_date")
    search_fields = ("code", "title")
    inlines = [EligibilityCriterionInline]


@admin.register(Study)
class StudyAdmin(admin.ModelAdmin):
    list_display = ("name", "protocol", "status", "target_enrollment")
    list_filter = ("status",)
    search_fields = ("name",)
    inlines = [ArmInline, VisitTemplateInline]


admin.site.register(EligibilityCriterion)
admin.site.register(VisitTemplate)
