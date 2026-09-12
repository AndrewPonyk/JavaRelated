from __future__ import annotations

from rest_framework import serializers

from apps.patients.models import Subject
from apps.trials.models import Study

from .models import Decision, Screening


class ScreeningSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    study = serializers.SlugRelatedField(slug_field="public_id", queryset=Study.objects.all())
    subject = serializers.SlugRelatedField(slug_field="public_id", queryset=Subject.objects.all())
    subject_code = serializers.CharField(source="subject.subject_code", read_only=True)
    # PHI: accepted on write, never returned.
    note_text = serializers.CharField(write_only=True, required=False, allow_blank=True)

    class Meta:
        model = Screening
        fields = (
            "id",
            "study",
            "subject",
            "subject_code",
            "status",
            "ml_score",
            "ml_recommendation",
            "rationale",
            "model_version",
            "ontology_versions",
            "human_decision",
            "decided_at",
            "note_text",
            "created_at",
        )
        read_only_fields = (
            "status",
            "ml_score",
            "ml_recommendation",
            "rationale",
            "model_version",
            "ontology_versions",
            "human_decision",
            "decided_at",
            "created_at",
        )


class DecisionSerializer(serializers.Serializer):
    decision = serializers.ChoiceField(choices=Decision.choices)
    password = serializers.CharField(write_only=True, required=False, allow_blank=True)
