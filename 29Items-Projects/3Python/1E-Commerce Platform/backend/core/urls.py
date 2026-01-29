"""URL configuration for E-Commerce Platform."""
from django.conf import settings
from django.contrib import admin
from django.http import JsonResponse
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from apps.checkout.views import OrderViewSet

# Orders router (separate from checkout for cleaner API)
orders_router = DefaultRouter()
orders_router.register(r'', OrderViewSet, basename='orders')

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/v1/auth/', include('apps.users.urls')),
    path('api/v1/products/', include('apps.products.urls')),
    path('api/v1/cart/', include('apps.cart.urls')),
    path('api/v1/checkout/', include('apps.checkout.urls')),
    path('api/v1/orders/', include(orders_router.urls)),
    path('api/v1/inventory/', include('apps.inventory.urls')),
    path('api/v1/vendors/', include('apps.vendors.urls')),
    path('api/v1/search/', include('apps.search.urls')),
    path('api/v1/recommendations/', include('apps.recommendations.urls')),
]


def health_check(request):
    """Health check endpoint for container orchestration."""
    return JsonResponse({'status': 'healthy'})


urlpatterns += [
    path('health/', health_check, name='health_check'),
]

# Debug toolbar URLs (development only)
if settings.DEBUG:
    try:
        import debug_toolbar
        urlpatterns = [
            path('__debug__/', include(debug_toolbar.urls)),
        ] + urlpatterns
    except ImportError:
        pass
