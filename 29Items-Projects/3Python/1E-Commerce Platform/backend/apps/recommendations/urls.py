"""Recommendations URL configuration."""
from django.urls import path

from .views import (
    FrequentlyBoughtTogetherView,
    PersonalizedRecommendationsView,
    SimilarProductsView,
    TrackInteractionView,
    TrendingProductsView,
)

urlpatterns = [
    path('personalized/', PersonalizedRecommendationsView.as_view(), name='recommendations-personalized'),
    path('similar/<uuid:product_id>/', SimilarProductsView.as_view(), name='recommendations-similar'),
    path('bought-together/<uuid:product_id>/', FrequentlyBoughtTogetherView.as_view(), name='recommendations-bought-together'),
    path('track/', TrackInteractionView.as_view(), name='recommendations-track'),
    path('trending/', TrendingProductsView.as_view(), name='recommendations-trending'),
]
