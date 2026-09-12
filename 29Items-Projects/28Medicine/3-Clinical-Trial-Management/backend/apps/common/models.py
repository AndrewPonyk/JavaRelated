"""Shared abstract base models.

Every domain model should inherit from ``BaseModel`` so that timestamps and a
stable public UUID are uniform across the system (the UUID is what we expose in
APIs — we never leak sequential primary keys).
"""
from __future__ import annotations

import uuid

from django.conf import settings
from django.db import models


class TimeStampedModel(models.Model):
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True


class BaseModel(TimeStampedModel):
    """UUID-exposed, timestamped base with light authorship tracking.

    Note: authorship here is convenience metadata. The authoritative,
    tamper-evident record of *who changed what* lives in ``apps.audit`` and is
    written in the same DB transaction as the change.
    """

    public_id = models.UUIDField(default=uuid.uuid4, editable=False, unique=True)
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.PROTECT,
        related_name="+",
    )

    class Meta:
        abstract = True
        ordering = ("-created_at",)
