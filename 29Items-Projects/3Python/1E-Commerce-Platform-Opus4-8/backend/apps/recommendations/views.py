"""Recommendation API — serves precomputed, cached results (no inference here)."""

from __future__ import annotations

from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema
from rest_framework import serializers, status
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.catalog.models import Product
from apps.catalog.serializers import ProductSerializer

from .ml import inference
from .models import Interaction


class RecommendationView(APIView):
    """GET /api/v1/recommendations/ — top-N items for the current user."""

    permission_classes = [IsAuthenticated]

    @extend_schema(responses=OpenApiTypes.OBJECT)
    def get(self, request: Request) -> Response:
        product_ids = inference.get_cached_recommendations(request.user.id)

        if product_ids:
            # Preserve the model's ranking order.
            products = list(
                Product.objects.filter(
                    id__in=product_ids, status=Product.Status.ACTIVE
                ).select_related("category", "vendor", "stock")
            )
            order = {pid: i for i, pid in enumerate(product_ids)}
            products.sort(key=lambda p: order.get(p.id, 1_000_000))
            source = "model"
        else:
            # Cold-start fallback: most recent active products.
            products = list(
                Product.objects.filter(status=Product.Status.ACTIVE)
                .select_related("category", "vendor", "stock")
                .order_by("-created_at")[:10]
            )
            source = "popularity_fallback"

        return Response({"source": source, "results": ProductSerializer(products, many=True).data})


class TrackInteractionSerializer(serializers.Serializer):
    product_id = serializers.IntegerField()
    event = serializers.ChoiceField(choices=[Interaction.Event.VIEW, Interaction.Event.ADD_TO_CART])

    def validate_product_id(self, value: int) -> int:
        if not Product.objects.filter(id=value).exists():
            raise serializers.ValidationError("Product does not exist.")
        return value


class TrackInteractionView(APIView):
    """POST /api/v1/recommendations/track/ — log a view/add-to-cart event."""

    permission_classes = [IsAuthenticated]

    @extend_schema(request=TrackInteractionSerializer, responses={201: None})
    def post(self, request: Request) -> Response:
        payload = TrackInteractionSerializer(data=request.data)
        payload.is_valid(raise_exception=True)
        weight = 1.0 if payload.validated_data["event"] == Interaction.Event.VIEW else 2.0
        Interaction.objects.create(
            user=request.user,
            product_id=payload.validated_data["product_id"],
            event=payload.validated_data["event"],
            weight=weight,
        )
        return Response(status=status.HTTP_201_CREATED)
