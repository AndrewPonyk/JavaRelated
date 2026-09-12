from django.urls import path

from .views import RecommendationView, TrackInteractionView

urlpatterns = [
    path("", RecommendationView.as_view(), name="recommendations"),
    path("track/", TrackInteractionView.as_view(), name="track-interaction"),
]
