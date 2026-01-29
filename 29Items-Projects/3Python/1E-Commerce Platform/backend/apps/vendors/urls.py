"""Vendor URL configuration."""
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import (
    VendorDashboardView,
    VendorPayoutViewSet,
    VendorProfileView,
    VendorPublicViewSet,
    VendorRegistrationView,
)

router = DefaultRouter()
router.register(r'public', VendorPublicViewSet, basename='vendors-public')
router.register(r'payouts', VendorPayoutViewSet, basename='vendor-payouts')

urlpatterns = [
    path('register/', VendorRegistrationView.as_view(), name='vendor-register'),
    path('profile/', VendorProfileView.as_view(), name='vendor-profile'),
    path('dashboard/', VendorDashboardView.as_view(), name='vendor-dashboard'),
    path('', include(router.urls)),
]
