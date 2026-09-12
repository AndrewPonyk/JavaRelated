from django.urls import path

from .views import CartItemDetailView, CartItemsView, CartView

urlpatterns = [
    path("", CartView.as_view(), name="cart"),
    path("items/", CartItemsView.as_view(), name="cart-items"),
    path("items/<int:pk>/", CartItemDetailView.as_view(), name="cart-item-detail"),
]
