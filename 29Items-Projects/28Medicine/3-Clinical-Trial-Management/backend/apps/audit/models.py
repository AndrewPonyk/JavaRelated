"""Append-only audit trail (FDA 21 CFR Part 11 §11.10(e)).

Design rules:
  * Records are **append-only** — no updates, no deletes. Enforced in app code
    here and reinforced at the DB grant level in production (the app's DB role is
    granted INSERT/SELECT only on this table).
  * Each event is **computer-generated, time-stamped** and captures the actor,
    the action, the target entity, a **before/after diff**, the reason-for-change
    and the source IP/correlation id.
  * A per-row hash chained to the previous row makes tampering detectable
    (verified nightly by ``apps.audit.tasks.verify_audit_integrity``).
"""
from __future__ import annotations

import hashlib
import json

from django.conf import settings
from django.db import models


class AuditAction(models.TextChoices):
    CREATE = "CREATE", "Create"
    UPDATE = "UPDATE", "Update"
    DELETE = "DELETE", "Delete"  # logical/soft delete — row is never physically removed
    READ = "READ", "Read (sensitive)"
    LOGIN = "LOGIN", "Login"
    ESIGN = "ESIGN", "Electronic signature"
    ML_SCREEN = "ML_SCREEN", "ML eligibility screening"
    DECISION = "DECISION", "Human eligibility decision"


class AuditEvent(models.Model):
    """One immutable audit record. Never mutate an instance after creation."""

    # Actor & context
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.PROTECT,
        related_name="audit_events",
    )
    actor_label = models.CharField(max_length=255, blank=True)  # denormalized, immutable
    action = models.CharField(max_length=16, choices=AuditAction.choices)
    correlation_id = models.CharField(max_length=64, blank=True, db_index=True)
    source_ip = models.GenericIPAddressField(null=True, blank=True)

    # Target (generic reference — keep loose coupling to any domain model)
    entity_type = models.CharField(max_length=120, db_index=True)  # e.g. "trials.Study"
    entity_id = models.CharField(max_length=64, db_index=True)

    # What changed + why
    changes = models.JSONField(default=dict, blank=True)  # {field: [before, after]}
    reason = models.TextField(blank=True)  # reason-for-change (Part 11)

    # Integrity
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    prev_hash = models.CharField(max_length=64, blank=True)
    row_hash = models.CharField(max_length=64, blank=True)

    class Meta:
        indexes = [models.Index(fields=["entity_type", "entity_id", "created_at"])]
        ordering = ("id",)

    def __str__(self) -> str:  # PHI-free by construction
        return f"{self.action} {self.entity_type}#{self.entity_id} by {self.actor_label}"

    # -- Immutability guards ------------------------------------------------
    def save(self, *args, **kwargs) -> None:
        if self.pk is not None:
            raise ValueError("AuditEvent is append-only; existing rows cannot be modified.")
        self.row_hash = self.compute_hash()
        super().save(*args, **kwargs)

    def delete(self, *args, **kwargs):  # pragma: no cover
        raise ValueError("AuditEvent is append-only; rows cannot be deleted.")

    def compute_hash(self) -> str:
        """Hash this row's content chained to ``prev_hash`` (tamper-evidence)."""
        material = json.dumps(
            {
                "actor": self.actor_label,
                "action": self.action,
                "entity": f"{self.entity_type}#{self.entity_id}",
                "changes": self.changes,
                "reason": self.reason,
                "prev": self.prev_hash,
            },
            sort_keys=True,
            default=str,
        )
        return hashlib.sha256(material.encode("utf-8")).hexdigest()
