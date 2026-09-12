from django.urls import path

from .views import AutocompleteView, ProductSearchView

urlpatterns = [
    path("", ProductSearchView.as_view(), name="product-search"),
    path("autocomplete/", AutocompleteView.as_view(), name="autocomplete"),
]
