"""In-app notifications.

Messages are addressed to a user and carry a de-identified subject reference
(never PHI). A ``dedupe_key`` makes delivery idempotent so a re-run of a Beat job
does not spam recipients.
"""
from __future__ import annotations

from django.db import models

from apps.common.models import TimeStampedModel


class NotificationKind(models.TextChoices):
    VISIT_REMINDER = "VISIT_REMINDER", "Visit reminder"
    VISIT_OVERDUE = "VISIT_OVERDUE", "Visit overdue"
    DEVIATION = "DEVIATION", "Protocol deviation"
    SCREENING_READY = "SCREENING_READY", "Screening ready for review"


class Notification(TimeStampedModel):
    recipient = models.ForeignKey(
        "accounts.User", on_delete=models.CASCADE, related_name="notifications"
    )
    kind = models.CharField(max_length=20, choices=NotificationKind.choices)
    subject = models.CharField(max_length=255)
    body = models.TextField(blank=True, default="")
    entity_type = models.CharField(max_length=120, blank=True, default="")
    entity_id = models.CharField(max_length=64, blank=True, default="")
    read = models.BooleanField(default=False)
    # Idempotency: unique per logical event so re-runs don't duplicate.
    dedupe_key = models.CharField(max_length=200, unique=True)

    class Meta:
        ordering = ("-created_at",)
        indexes = [models.Index(fields=["recipient", "read"])]

    def __str__(self) -> str:
        return f"{self.kind} -> {self.recipient_id}"
