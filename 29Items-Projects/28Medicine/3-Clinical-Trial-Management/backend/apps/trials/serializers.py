"""DRF serializers — the input-validation boundary (ARCHITECTURE §2.5)."""
from __future__ import annotations

from rest_framework import serializers

from .models import (
    Arm,
    CriterionType,
    EligibilityCriterion,
    Protocol,
    Study,
    VisitTemplate,
)


class ArmSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)

    class Meta:
        model = Arm
        fields = ("id", "name", "description", "allocation_ratio")


class EligibilityCriterionSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    protocol = serializers.SlugRelatedField(slug_field="public_id", queryset=Protocol.objects.all())

    class Meta:
        model = EligibilityCriterion
        fields = ("id", "protocol", "type", "order", "text", "coded_rule")

    def validate_type(self, value: str) -> str:
        if value not in CriterionType.values:
            raise serializers.ValidationError("type must be INCLUSION or EXCLUSION.")
        return value


class VisitTemplateSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)

    class Meta:
        model = VisitTemplate
        fields = (
            "id",
            "name",
            "day_offset",
            "window_before_days",
            "window_after_days",
        )


class ProtocolSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    criteria = EligibilityCriterionSerializer(many=True, read_only=True)

    class Meta:
        model = Protocol
        fields = (
            "id",
            "code",
            "title",
            "version",
            "phase",
            "summary",
            "effective_date",
            "criteria",
        )

    def validate(self, attrs: dict) -> dict:
        code = attrs.get("code", getattr(self.instance, "code", None))
        version = attrs.get("version", getattr(self.instance, "version", None))
        qs = Protocol.objects.filter(code=code, version=version)
        if self.instance:
            qs = qs.exclude(pk=self.instance.pk)
        if qs.exists():
            raise serializers.ValidationError(
                {"version": f"Protocol {code} v{version} already exists."}
            )
        return attrs


class StudySerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    protocol = serializers.SlugRelatedField(slug_field="public_id", queryset=Protocol.objects.all())
    arms = ArmSerializer(many=True, read_only=True)
    visit_templates = VisitTemplateSerializer(many=True, read_only=True)

    class Meta:
        model = Study
        fields = (
            "id",
            "protocol",
            "name",
            "status",
            "target_enrollment",
            "planned_start",
            "planned_end",
            "arms",
            "visit_templates",
            "created_at",
        )
        read_only_fields = ("status", "created_at")  # status changes go via /open

    def validate(self, attrs: dict) -> dict:
        start = attrs.get("planned_start") or getattr(self.instance, "planned_start", None)
        end = attrs.get("planned_end") or getattr(self.instance, "planned_end", None)
        if start and end and end < start:
            raise serializers.ValidationError(
                {"planned_end": "planned_end cannot be before planned_start."}
            )
        if attrs.get("target_enrollment", 0) < 0:
            raise serializers.ValidationError({"target_enrollment": "Must be non-negative."})
        return attrs


class ArmWriteSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    study = serializers.SlugRelatedField(slug_field="public_id", queryset=Study.objects.all())

    class Meta:
        model = Arm
        fields = ("id", "study", "name", "description", "allocation_ratio")
