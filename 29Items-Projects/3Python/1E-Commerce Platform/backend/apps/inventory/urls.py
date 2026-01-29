"""Inventory URL configuration."""
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import InventoryViewSet, StockAlertViewSet

router = DefaultRouter()
router.register(r'items', InventoryViewSet, basename='inventory')
router.register(r'alerts', StockAlertViewSet, basename='stock-alerts')

urlpatterns = [
    path('', include(router.urls)),
]
