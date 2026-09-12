"""Enrollment workflow services.

Guarded transitions enforce the protocol: a subject must consent before being
randomized, and can only be randomized once. Consent and randomization capture
an electronic signature (21 CFR Part 11).
"""
from __future__ import annotations

import datetime
import secrets

from django.db import transaction
from django.db.models import Count
from django.utils import timezone

from apps.accounts.services import sign
from apps.common.exceptions import EnrollmentError
from apps.trials.models import Arm, Study, StudyStatus

from .models import (
    Deviation,
    DeviationKind,
    Enrollment,
    EnrollmentStatus,
    Visit,
    VisitStatus,
)

ENTITY = "enrollment.Enrollment"


@transaction.atomic
def create_enrollment(*, study: Study, subject, created_by=None) -> Enrollment:
    if study.status != StudyStatus.OPEN:
        raise EnrollmentError("Study is not open to enrollment.")
    if Enrollment.objects.filter(study=study, subject=subject).exists():
        raise EnrollmentError("Subject is already enrolled in this study.")
    return Enrollment.objects.create(study=study, subject=subject, created_by=created_by)


@transaction.atomic
def screen_fail(enrollment: Enrollment, *, reason: str = "") -> Enrollment:
    if enrollment.status != EnrollmentStatus.SCREENING:
        raise EnrollmentError("Only screening subjects can be screen-failed.")
    enrollment.status = EnrollmentStatus.SCREEN_FAILED
    enrollment.withdrawal_reason = reason
    enrollment.save(update_fields=["status", "withdrawal_reason", "updated_at"])
    return enrollment


@transaction.atomic
def give_consent(enrollment: Enrollment, *, signer, password: str | None = None) -> Enrollment:
    if enrollment.status != EnrollmentStatus.SCREENING:
        raise EnrollmentError("Consent can only be recorded during screening.")
    enrollment.status = EnrollmentStatus.CONSENTED
    enrollment.consent_signed_at = timezone.now()
    enrollment.save(update_fields=["status", "consent_signed_at", "updated_at"])
    sign(
        signer=signer,
        meaning="Informed consent",
        entity_type=ENTITY,
        entity_id=enrollment.pk,
        password=password,
    )
    return enrollment


def _choose_arm(study: Study) -> Arm:
    """Balanced randomization: minimize (assigned / allocation_ratio) per arm."""
    arms = list(study.arms.all())
    if not arms:
        raise EnrollmentError("Study has no arms to randomize into.")
    counts = {
        row["arm"]: row["n"]
        for row in Enrollment.objects.filter(study=study, arm__isnull=False)
        .values("arm")
        .annotate(n=Count("id"))
    }
    # Lower load = more likely; unpredictable tiebreak (secrets) for fairness.
    rng = secrets.SystemRandom()
    scored = sorted(
        arms,
        key=lambda a: (counts.get(a.pk, 0) / max(a.allocation_ratio, 1), rng.random()),
    )
    return scored[0]


@transaction.atomic
def randomize(enrollment: Enrollment, *, signer, password: str | None = None) -> Enrollment:
    if enrollment.status != EnrollmentStatus.CONSENTED:
        raise EnrollmentError("Subject must be consented before randomization.")
    enrollment.arm = _choose_arm(enrollment.study)
    enrollment.status = EnrollmentStatus.RANDOMIZED
    enrollment.randomized_at = timezone.now()
    enrollment.save(update_fields=["arm", "status", "randomized_at", "updated_at"])
    sign(
        signer=signer,
        meaning="Randomization",
        entity_type=ENTITY,
        entity_id=enrollment.pk,
        password=password,
    )
    return enrollment


@transaction.atomic
def activate(enrollment: Enrollment, *, baseline_date: datetime.date | None = None) -> Enrollment:
    """RANDOMIZED -> ENROLLED, set baseline and schedule visits."""
    if enrollment.status != EnrollmentStatus.RANDOMIZED:
        raise EnrollmentError("Only randomized subjects can be activated.")
    enrollment.baseline_date = baseline_date or timezone.now().date()
    enrollment.status = EnrollmentStatus.ENROLLED
    enrollment.save(update_fields=["baseline_date", "status", "updated_at"])
    enrollment.subject.enrolled = True
    enrollment.subject.save(update_fields=["enrolled", "updated_at"])
    _schedule_visits(enrollment)
    return enrollment


def _schedule_visits(enrollment: Enrollment) -> int:
    base = enrollment.baseline_date
    if base is None:  # defensive: visits can only be scheduled from a baseline
        raise EnrollmentError("Cannot schedule visits before a baseline date is set.")
    created = 0
    for template in enrollment.study.visit_templates.all():
        _, was_created = Visit.objects.get_or_create(
            enrollment=enrollment,
            template=template,
            defaults={"scheduled_date": base + datetime.timedelta(days=template.day_offset)},
        )
        created += int(was_created)
    return created


@transaction.atomic
def withdraw(enrollment: Enrollment, *, reason: str = "") -> Enrollment:
    if enrollment.status in {EnrollmentStatus.WITHDRAWN, EnrollmentStatus.SCREEN_FAILED}:
        raise EnrollmentError("Enrollment is already closed.")
    enrollment.status = EnrollmentStatus.WITHDRAWN
    enrollment.withdrawal_reason = reason
    enrollment.save(update_fields=["status", "withdrawal_reason", "updated_at"])
    return enrollment


def detect_deviations(*, today: datetime.date | None = None) -> int:
    """Create deviation records for overdue/missed visits. Idempotent.

    A visit whose allowed window has closed without an ``actual_date`` is a
    missed-visit deviation. Re-running creates no duplicates (unique constraint).
    """
    today = today or timezone.now().date()
    created = 0
    overdue = (
        Visit.objects.filter(actual_date__isnull=True)
        .exclude(status=VisitStatus.COMPLETED)
        .select_related("enrollment", "template")
    )
    for visit in overdue.iterator():
        window_end = visit.scheduled_date + datetime.timedelta(
            days=visit.template.window_after_days
        )
        if today <= window_end:
            continue
        if visit.enrollment.status not in {
            EnrollmentStatus.ENROLLED,
            EnrollmentStatus.RANDOMIZED,
        }:
            continue
        _, was_created = Deviation.objects.get_or_create(
            enrollment=visit.enrollment,
            template=visit.template,
            kind=DeviationKind.MISSED_VISIT,
            defaults={
                "detail": f"Visit '{visit.template.name}' missed; window closed {window_end}.",
            },
        )
        if was_created:
            visit.status = VisitStatus.MISSED
            visit.save(update_fields=["status", "updated_at"])
            created += 1
    return created
