"""User URL configuration."""
from django.urls import include, path
from rest_framework.routers import DefaultRouter
from rest_framework_simplejwt.views import TokenRefreshView

from .views import (
    AddressViewSet,
    CustomTokenObtainPairView,
    PasswordChangeView,
    UserProfileView,
    UserRegistrationView,
    WishlistViewSet,
)

router = DefaultRouter()
router.register(r'addresses', AddressViewSet, basename='addresses')
router.register(r'wishlist', WishlistViewSet, basename='wishlist')

urlpatterns = [
    # Authentication
    path('register/', UserRegistrationView.as_view(), name='register'),
    path('login/', CustomTokenObtainPairView.as_view(), name='token_obtain_pair'),
    path('refresh/', TokenRefreshView.as_view(), name='token_refresh'),

    # Profile
    path('profile/', UserProfileView.as_view(), name='profile'),
    path('password/change/', PasswordChangeView.as_view(), name='password_change'),

    # Addresses
    path('', include(router.urls)),
]
