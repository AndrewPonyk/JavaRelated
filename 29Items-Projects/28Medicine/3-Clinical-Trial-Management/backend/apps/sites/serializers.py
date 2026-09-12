from __future__ import annotations

from rest_framework import serializers

from .models import DelegationLog, Site


class SiteSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    pi_username = serializers.CharField(
        source="principal_investigator.username", read_only=True, default=None
    )

    class Meta:
        model = Site
        fields = (
            "id",
            "name",
            "institution",
            "principal_investigator",
            "pi_username",
            "created_at",
        )
        read_only_fields = ("created_at",)


class DelegationLogSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(source="public_id", read_only=True)
    site = serializers.SlugRelatedField(slug_field="public_id", queryset=Site.objects.all())
    is_active = serializers.BooleanField(read_only=True)

    class Meta:
        model = DelegationLog
        fields = ("id", "site", "user", "task", "granted_on", "revoked_on", "is_active")
