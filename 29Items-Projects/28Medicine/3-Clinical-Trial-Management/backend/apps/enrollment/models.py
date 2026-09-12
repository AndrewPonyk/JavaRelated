"""Enrollment workflow: screening -> e-consent -> randomization -> active.

Modeled as an explicit state machine so transitions are auditable and a subject
cannot, e.g., be randomized before consent. Consent and randomization are
e-signed events (21 CFR Part 11). Visits and Deviations support the scheduled
protocol-deviation detection job (ARCHITECTURE Flow B).
"""
from __future__ import annotations

from django.db import models

from apps.common.models import BaseModel


class EnrollmentStatus(models.TextChoices):
    SCREENING = "SCREENING", "Screening"
    CONSENTED = "CONSENTED", "Informed consent signed"
    RANDOMIZED = "RANDOMIZED", "Randomized"
    ENROLLED = "ENROLLED", "Enrolled / active"
    WITHDRAWN = "WITHDRAWN", "Withdrawn"
    SCREEN_FAILED = "SCREEN_FAILED", "Screen failed"


class Enrollment(BaseModel):
    study = models.ForeignKey("trials.Study", on_delete=models.PROTECT, related_name="enrollments")
    subject = models.ForeignKey(
        "patients.Subject", on_delete=models.PROTECT, related_name="enrollments"
    )
    arm = models.ForeignKey(
        "trials.Arm", null=True, blank=True, on_delete=models.PROTECT, related_name="enrollments"
    )
    status = models.CharField(
        max_length=16, choices=EnrollmentStatus.choices, default=EnrollmentStatus.SCREENING
    )
    consent_signed_at = models.DateTimeField(null=True, blank=True)
    randomized_at = models.DateTimeField(null=True, blank=True)
    baseline_date = models.DateField(null=True, blank=True)  # anchors visit windows
    withdrawal_reason = models.TextField(blank=True, default="")

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["study", "subject"], name="uniq_enrollment_study_subject"
            )
        ]
        indexes = [models.Index(fields=["status"])]

    def __str__(self) -> str:
        return f"{self.subject_id} @ {self.study_id} ({self.status})"


class VisitStatus(models.TextChoices):
    SCHEDULED = "SCHEDULED", "Scheduled"
    COMPLETED = "COMPLETED", "Completed"
    MISSED = "MISSED", "Missed"


class Visit(BaseModel):
    """An actual study visit instance for an enrollment, per a VisitTemplate."""

    enrollment = models.ForeignKey(Enrollment, on_delete=models.CASCADE, related_name="visits")
    template = models.ForeignKey(
        "trials.VisitTemplate", on_delete=models.PROTECT, related_name="visits"
    )
    scheduled_date = models.DateField()
    actual_date = models.DateField(null=True, blank=True)
    status = models.CharField(
        max_length=12, choices=VisitStatus.choices, default=VisitStatus.SCHEDULED
    )

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["enrollment", "template"], name="uniq_visit_enrollment_template"
            )
        ]
        ordering = ("scheduled_date",)


class DeviationKind(models.TextChoices):
    MISSED_VISIT = "MISSED_VISIT", "Missed visit"
    OUT_OF_WINDOW = "OUT_OF_WINDOW", "Visit out of window"


class Deviation(BaseModel):
    """A recorded protocol deviation (created by the scheduled detection job)."""

    enrollment = models.ForeignKey(Enrollment, on_delete=models.CASCADE, related_name="deviations")
    template = models.ForeignKey(
        "trials.VisitTemplate", on_delete=models.PROTECT, related_name="deviations"
    )
    kind = models.CharField(max_length=16, choices=DeviationKind.choices)
    detail = models.TextField(blank=True, default="")
    resolved = models.BooleanField(default=False)

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["enrollment", "template", "kind"],
                name="uniq_deviation_enrollment_template_kind",
            )
        ]

    def __str__(self) -> str:
        return f"{self.kind} · enrollment {self.enrollment_id}"
