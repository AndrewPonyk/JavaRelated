"""Subject serializers. PHI fields are write-only or role-gated."""
from __future__ import annotations

from rest_framework import serializers

from apps.accounts.models import Role

from .models import Subject

# Roles permitted to read decrypted PHI (minimum-necessary).
PHI_VISIBLE_ROLES = {Role.PI, Role.CRC}


class SubjectSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    subject_code = serializers.CharField(read_only=True)  # system-generated
    age = serializers.IntegerField(read_only=True)

    class Meta:
        model = Subject
        fields = (
            "id",
            "subject_code",
            "first_name",
            "last_name",
            "medical_record_number",
            "date_of_birth",
            "sex_at_birth",
            "age",
            "enrolled",
            "created_at",
        )
        read_only_fields = ("enrolled", "created_at")

    def to_representation(self, instance: Subject) -> dict:
        """Redact PHI for roles that don't need it (minimum-necessary)."""
        data = super().to_representation(instance)
        request = self.context.get("request")
        role = getattr(getattr(request, "user", None), "role", None)
        if role not in PHI_VISIBLE_ROLES:
            for field in ("first_name", "last_name", "medical_record_number", "date_of_birth"):
                data[field] = "***"
        return data

    def validate_date_of_birth(self, value: str) -> str:
        if value and len(value) < 8:
            raise serializers.ValidationError("Expected an ISO date (YYYY-MM-DD).")
        return value
