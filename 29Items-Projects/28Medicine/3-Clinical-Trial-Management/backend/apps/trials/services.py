"""Service layer for the trials context.

Views stay thin; business rules live here. Cross-app interactions (e.g. counting
enrollments) go through service functions / late imports, never by reaching into
another app's ORM internals (ARCHITECTURE §2.1).
"""
from __future__ import annotations

from django.core.exceptions import ValidationError
from django.db import transaction

from .models import Study, StudyStatus


class StudyStateError(ValidationError):
    """Raised when a study state transition is not allowed."""


@transaction.atomic
def open_study(study: Study) -> Study:
    """DRAFT/PAUSED → OPEN. Requires at least one arm. Audited via post_save."""
    if study.status not in {StudyStatus.DRAFT, StudyStatus.PAUSED}:
        raise StudyStateError(f"Cannot open a study in status '{study.status}'.")
    if not study.arms.exists():
        raise StudyStateError("A study needs at least one arm before opening.")
    study.status = StudyStatus.OPEN
    study.save(update_fields=["status", "updated_at"])
    return study


@transaction.atomic
def close_study(study: Study) -> Study:
    """OPEN/PAUSED → CLOSED."""
    if study.status not in {StudyStatus.OPEN, StudyStatus.PAUSED}:
        raise StudyStateError(f"Cannot close a study in status '{study.status}'.")
    study.status = StudyStatus.CLOSED
    study.save(update_fields=["status", "updated_at"])
    return study


def enrollment_progress(study: Study) -> dict:
    """Return enrollment progress counted from the enrollment context."""
    from apps.enrollment.models import Enrollment, EnrollmentStatus

    enrolled = (
        Enrollment.objects.filter(study=study)
        .exclude(status__in=[EnrollmentStatus.SCREEN_FAILED, EnrollmentStatus.WITHDRAWN])
        .count()
    )
    target = study.target_enrollment or 0
    pct = round(100 * enrolled / target, 1) if target else 0.0
    return {"enrolled": enrolled, "target": target, "percent": pct}
