from __future__ import annotations

from rest_framework import serializers

from apps.patients.models import Subject
from apps.trials.models import Study

from .models import Deviation, Enrollment, Visit


class EnrollmentSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    study = serializers.SlugRelatedField(slug_field="public_id", queryset=Study.objects.all())
    subject = serializers.SlugRelatedField(slug_field="public_id", queryset=Subject.objects.all())
    subject_code = serializers.CharField(source="subject.subject_code", read_only=True)
    arm_name = serializers.CharField(source="arm.name", read_only=True, default=None)

    class Meta:
        model = Enrollment
        fields = (
            "id",
            "study",
            "subject",
            "subject_code",
            "arm_name",
            "status",
            "consent_signed_at",
            "randomized_at",
            "baseline_date",
            "withdrawal_reason",
            "created_at",
        )
        read_only_fields = (
            "status",
            "consent_signed_at",
            "randomized_at",
            "baseline_date",
            "withdrawal_reason",
            "created_at",
        )


class VisitSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    template_name = serializers.CharField(source="template.name", read_only=True)

    class Meta:
        model = Visit
        fields = (
            "id",
            "enrollment",
            "template",
            "template_name",
            "scheduled_date",
            "actual_date",
            "status",
        )
        read_only_fields = ("enrollment", "template", "scheduled_date")


class DeviationSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)

    class Meta:
        model = Deviation
        fields = ("id", "enrollment", "template", "kind", "detail", "resolved", "created_at")
        read_only_fields = fields


# ── action payloads ───────────────────────────────────────────────────────
class SignActionSerializer(serializers.Serializer):
    password = serializers.CharField(write_only=True, required=False, allow_blank=True)


class WithdrawSerializer(serializers.Serializer):
    reason = serializers.CharField(required=False, allow_blank=True, default="")


class ActivateSerializer(serializers.Serializer):
    baseline_date = serializers.DateField(required=False)
