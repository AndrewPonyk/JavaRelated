from django.contrib import admin

from .models import DataPoint, FormDefinition, Query


@admin.register(FormDefinition)
class FormDefinitionAdmin(admin.ModelAdmin):
    list_display = ("name", "study")
    search_fields = ("name",)


@admin.register(DataPoint)
class DataPointAdmin(admin.ModelAdmin):
    list_display = ("enrollment", "form", "field_name", "sdv_verified", "created_at")
    list_filter = ("sdv_verified", "form")


@admin.register(Query)
class QueryAdmin(admin.ModelAdmin):
    list_display = ("data_point", "status", "created_at")
    list_filter = ("status",)
