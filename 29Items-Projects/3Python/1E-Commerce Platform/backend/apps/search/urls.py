"""Search URL configuration."""
from django.urls import path

from .views import ProductSearchView, SearchSuggestView, TrendingSearchesView

urlpatterns = [
    path('products/', ProductSearchView.as_view(), name='search-products'),
    path('suggest/', SearchSuggestView.as_view(), name='search-suggest'),
    path('trending/', TrendingSearchesView.as_view(), name='search-trending'),
]
