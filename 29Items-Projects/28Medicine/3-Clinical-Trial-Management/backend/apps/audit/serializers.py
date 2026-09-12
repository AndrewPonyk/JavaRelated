from __future__ import annotations

from rest_framework import serializers

from .models import AuditEvent


class AuditEventSerializer(serializers.ModelSerializer):
    class Meta:
        model = AuditEvent
        fields = (
            "id",
            "actor_label",
            "action",
            "entity_type",
            "entity_id",
            "changes",
            "reason",
            "correlation_id",
            "source_ip",
            "created_at",
        )
        read_only_fields = fields
