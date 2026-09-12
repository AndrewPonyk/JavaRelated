"""Trial protocol domain model (Deliverable 4.3 — Database Schema).

Entity relationships:

    Protocol 1──* Study 1──* Arm
       │                      ▲
       │ 1                    │ (subjects randomized to an arm — see enrollment app)
       *                      │
    EligibilityCriterion      │
                              │
    Study 1──* VisitTemplate ─┘ (defines the visit schedule / windows)

A ``Protocol`` is the versioned trial design document. A ``Study`` is a concrete
instance of running that protocol (often per-region/sponsor). Inclusion/exclusion
``EligibilityCriterion`` rows are what the ML pipeline matches EHR notes against.
"""
from __future__ import annotations

from django.db import models

from apps.common.models import BaseModel


class StudyStatus(models.TextChoices):
    DRAFT = "DRAFT", "Draft"
    OPEN = "OPEN", "Open to enrollment"
    PAUSED = "PAUSED", "Enrollment paused"
    CLOSED = "CLOSED", "Closed to enrollment"
    COMPLETED = "COMPLETED", "Completed"


class CriterionType(models.TextChoices):
    INCLUSION = "INCLUSION", "Inclusion"
    EXCLUSION = "EXCLUSION", "Exclusion"


class Protocol(BaseModel):
    """Versioned trial design. A new version is a new row (immutable history)."""

    code = models.CharField(max_length=64, db_index=True)  # sponsor protocol no.
    title = models.CharField(max_length=512)
    version = models.CharField(max_length=32, default="1.0")
    phase = models.CharField(max_length=16, blank=True)  # e.g. "II", "III"
    summary = models.TextField(blank=True)
    effective_date = models.DateField(null=True, blank=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["code", "version"], name="uniq_protocol_code_version")
        ]

    def __str__(self) -> str:
        return f"{self.code} v{self.version}"


class Study(BaseModel):
    protocol = models.ForeignKey(Protocol, on_delete=models.PROTECT, related_name="studies")
    name = models.CharField(max_length=255)
    status = models.CharField(max_length=16, choices=StudyStatus.choices, default=StudyStatus.DRAFT)
    target_enrollment = models.PositiveIntegerField(default=0)
    planned_start = models.DateField(null=True, blank=True)
    planned_end = models.DateField(null=True, blank=True)

    class Meta:
        verbose_name_plural = "studies"
        indexes = [models.Index(fields=["status"])]

    def __str__(self) -> str:
        return self.name

    @property
    def is_open(self) -> bool:
        return self.status == StudyStatus.OPEN


class Arm(BaseModel):
    """A treatment arm subjects are randomized into (e.g. Treatment / Placebo)."""

    study = models.ForeignKey(Study, on_delete=models.CASCADE, related_name="arms")
    name = models.CharField(max_length=255)
    description = models.TextField(blank=True)
    allocation_ratio = models.PositiveSmallIntegerField(default=1)  # e.g. 1:1 → 1

    def __str__(self) -> str:
        return f"{self.study.name} · {self.name}"


class EligibilityCriterion(BaseModel):
    """One inclusion/exclusion rule the ML pipeline evaluates against EHR data."""

    protocol = models.ForeignKey(Protocol, on_delete=models.CASCADE, related_name="criteria")
    type = models.CharField(max_length=10, choices=CriterionType.choices)
    order = models.PositiveSmallIntegerField(default=0)
    text = models.TextField()  # human-readable criterion
    # Structured/coded form used by the matcher (concepts + comparators).
    # e.g. {"concept": "SNOMED:73211009", "label": "Diabetes mellitus",
    #       "comparator": "present"}  or lab-value bounds.
    coded_rule = models.JSONField(default=dict, blank=True)

    class Meta:
        ordering = ("type", "order")

    def __str__(self) -> str:
        return f"[{self.type}] {self.text[:60]}"


class VisitTemplate(BaseModel):
    """Defines a scheduled study visit and its allowed window (for deviation checks)."""

    study = models.ForeignKey(Study, on_delete=models.CASCADE, related_name="visit_templates")
    name = models.CharField(max_length=255)  # e.g. "Baseline", "Week 4"
    day_offset = models.IntegerField(help_text="Days from enrollment/baseline.")
    window_before_days = models.PositiveSmallIntegerField(default=3)
    window_after_days = models.PositiveSmallIntegerField(default=3)

    class Meta:
        ordering = ("day_offset",)

    def __str__(self) -> str:
        return f"{self.study.name} · {self.name} (D{self.day_offset})"
