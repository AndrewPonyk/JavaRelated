from __future__ import annotations

from rest_framework import serializers

from .models import Notification


class NotificationSerializer(serializers.ModelSerializer):
    class Meta:
        model = Notification
        fields = (
            "id",
            "kind",
            "subject",
            "body",
            "entity_type",
            "entity_id",
            "read",
            "created_at",
        )
        read_only_fields = fields
