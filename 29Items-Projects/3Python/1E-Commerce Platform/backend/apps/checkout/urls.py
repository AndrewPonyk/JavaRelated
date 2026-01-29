"""Checkout URL configuration."""
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from .views import (
    CheckoutView,
    CouponValidateView,
    OrderViewSet,
    PaymentConfirmView,
)

router = DefaultRouter()
router.register(r'orders', OrderViewSet, basename='orders')

urlpatterns = [
    path('', include(router.urls)),
    path('initiate/', CheckoutView.as_view(), name='checkout-initiate'),
    path('confirm/', PaymentConfirmView.as_view(), name='checkout-confirm'),
    path('coupon/validate/', CouponValidateView.as_view(), name='coupon-validate'),
]
