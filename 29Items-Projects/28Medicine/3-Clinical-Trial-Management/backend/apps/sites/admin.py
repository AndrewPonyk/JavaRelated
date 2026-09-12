from django.contrib import admin

from .models import DelegationLog, Site


@admin.register(Site)
class SiteAdmin(admin.ModelAdmin):
    list_display = ("name", "institution", "principal_investigator")
    search_fields = ("name", "institution")
    filter_horizontal = ("studies",)


@admin.register(DelegationLog)
class DelegationLogAdmin(admin.ModelAdmin):
    list_display = ("user", "task", "site", "granted_on", "revoked_on")
    list_filter = ("site",)
