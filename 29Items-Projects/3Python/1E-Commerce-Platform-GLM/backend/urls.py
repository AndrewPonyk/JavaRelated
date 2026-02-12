"""
Root URL Configuration for E-Commerce Platform

This is the main URL configuration that includes the API URLs.
"""

from django.contrib import admin
from django.urls import path, include

urlpatterns = [
    # Django admin
    path("admin/", admin.site.urls),
    # API endpoints are under /api/ prefix
    path("api/", include("backend.api.urls")),
]
