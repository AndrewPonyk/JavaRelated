"""Eligibility screening records.

The ML result is **advisory**. ``Screening`` moves through:
    PENDING → (worker runs NLP) → AWAITING_REVIEW → (human e-signs) → DECIDED
A failure leaves it in FAILED — it never silently marks a subject (in)eligible.
"""
from __future__ import annotations

from django.db import models

from apps.common.fields import EncryptedCharField
from apps.common.models import BaseModel


class ScreeningStatus(models.TextChoices):
    PENDING = "PENDING", "Pending ML screening"
    AWAITING_REVIEW = "AWAITING_REVIEW", "Awaiting human review"
    DECIDED = "DECIDED", "Human decision recorded"
    FAILED = "FAILED", "Screening failed"


class Decision(models.TextChoices):
    ELIGIBLE = "ELIGIBLE", "Eligible"
    INELIGIBLE = "INELIGIBLE", "Ineligible"
    UNDETERMINED = "UNDETERMINED", "Undetermined"


class Screening(BaseModel):
    study = models.ForeignKey("trials.Study", on_delete=models.PROTECT, related_name="screenings")
    subject = models.ForeignKey(
        "patients.Subject", on_delete=models.PROTECT, related_name="screenings"
    )

    status = models.CharField(
        max_length=16, choices=ScreeningStatus.choices, default=ScreeningStatus.PENDING
    )

    # Source EHR/clinical note (PHI — encrypted at rest; de-identified by the ML
    # pipeline before processing).
    note_text = EncryptedCharField(blank=True, default="")

    # ── ML output (advisory) ──────────────────────────────────────────────
    ml_score = models.FloatField(null=True, blank=True)  # 0..1 confidence
    ml_recommendation = models.CharField(max_length=16, choices=Decision.choices, blank=True)
    # Per-criterion rationale for explainability + audit, e.g.
    # [{"criterion_id": "...", "type": "INCLUSION", "matched": true,
    #   "evidence": "…note span…", "concept": "SNOMED:73211009"}]
    rationale = models.JSONField(default=list, blank=True)
    model_version = models.CharField(max_length=64, blank=True)  # reproducibility
    ontology_versions = models.JSONField(default=dict, blank=True)

    # ── Authoritative human decision ──────────────────────────────────────
    human_decision = models.CharField(max_length=16, choices=Decision.choices, blank=True)
    decided_by = models.ForeignKey(
        "accounts.User", null=True, blank=True, on_delete=models.PROTECT, related_name="+"
    )
    decided_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        indexes = [models.Index(fields=["status"])]

    def __str__(self) -> str:
        return f"Screening {self.subject_id} · {self.status}"
