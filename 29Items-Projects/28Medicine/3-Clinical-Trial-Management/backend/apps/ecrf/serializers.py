from __future__ import annotations

from rest_framework import serializers

from apps.enrollment.models import Enrollment

from .models import DataPoint, FormDefinition, Query
from .services import validate_datapoint


class FormDefinitionSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    study = serializers.SlugRelatedField(slug_field="public_id", read_only=True)

    class Meta:
        model = FormDefinition
        fields = ("id", "study", "name", "schema")


class DataPointSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    enrollment = serializers.SlugRelatedField(
        slug_field="public_id", queryset=Enrollment.objects.all()
    )
    form = serializers.SlugRelatedField(
        slug_field="public_id", queryset=FormDefinition.objects.all()
    )

    class Meta:
        model = DataPoint
        fields = ("id", "enrollment", "form", "field_name", "value", "sdv_verified", "created_at")
        read_only_fields = ("sdv_verified", "created_at")

    def validate(self, attrs: dict) -> dict:
        form = attrs.get("form") or getattr(self.instance, "form", None)
        field_name = attrs.get("field_name") or getattr(self.instance, "field_name", None)
        value = attrs.get("value", getattr(self.instance, "value", None))
        if form and field_name is not None:
            validate_datapoint(form, field_name, value)  # raises EditCheckError (400)
        return attrs


class QuerySerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    data_point = serializers.SlugRelatedField(
        slug_field="public_id", queryset=DataPoint.objects.all()
    )

    class Meta:
        model = Query
        fields = ("id", "data_point", "status", "message", "created_at")
        read_only_fields = ("created_at",)
