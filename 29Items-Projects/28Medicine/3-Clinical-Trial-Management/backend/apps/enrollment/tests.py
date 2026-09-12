from __future__ import annotations

import datetime

import pytest

from apps.common.exceptions import EnrollmentError
from apps.enrollment import services
from apps.enrollment.models import Deviation, EnrollmentStatus, Visit
from apps.patients import services as patient_services
from apps.trials.models import VisitTemplate

pytestmark = pytest.mark.django_db


def _enroll(open_study, subject, user):
    return services.create_enrollment(study=open_study, subject=subject, created_by=user)


def test_cannot_enroll_into_unopened_study(study, subject, crc):
    with pytest.raises(EnrollmentError):
        services.create_enrollment(study=study, subject=subject, created_by=crc)


def test_no_duplicate_enrollment(open_study, subject, crc):
    _enroll(open_study, subject, crc)
    with pytest.raises(EnrollmentError):
        _enroll(open_study, subject, crc)


def test_full_happy_path(open_study, subject, crc):
    enr = _enroll(open_study, subject, crc)
    assert enr.status == EnrollmentStatus.SCREENING

    services.give_consent(enr, signer=crc)
    enr.refresh_from_db()
    assert enr.status == EnrollmentStatus.CONSENTED
    assert enr.consent_signed_at is not None

    services.randomize(enr, signer=crc)
    enr.refresh_from_db()
    assert enr.status == EnrollmentStatus.RANDOMIZED
    assert enr.arm is not None

    services.activate(enr, baseline_date=datetime.date(2025, 1, 1))
    enr.refresh_from_db()
    assert enr.status == EnrollmentStatus.ENROLLED
    assert enr.subject.enrolled is True


def test_cannot_randomize_before_consent(open_study, subject, crc):
    enr = _enroll(open_study, subject, crc)
    with pytest.raises(EnrollmentError):
        services.randomize(enr, signer=crc)


def test_randomization_uses_all_arms(open_study, crc):
    arms_used = set()
    for i in range(6):
        subj = patient_services.create_subject(first_name=f"P{i}")
        enr = _enroll(open_study, subj, crc)
        services.give_consent(enr, signer=crc)
        services.randomize(enr, signer=crc)
        enr.refresh_from_db()
        arms_used.add(enr.arm_id)
    assert len(arms_used) == 2  # both arms received assignments


def test_visits_scheduled_on_activation(open_study, subject, crc):
    VisitTemplate.objects.create(study=open_study, name="Baseline", day_offset=0)
    VisitTemplate.objects.create(study=open_study, name="Week 4", day_offset=28)
    enr = _enroll(open_study, subject, crc)
    services.give_consent(enr, signer=crc)
    services.randomize(enr, signer=crc)
    services.activate(enr, baseline_date=datetime.date(2025, 1, 1))
    assert Visit.objects.filter(enrollment=enr).count() == 2


def test_deviation_detection_is_idempotent(open_study, subject, crc):
    VisitTemplate.objects.create(
        study=open_study, name="Baseline", day_offset=0, window_after_days=3
    )
    enr = _enroll(open_study, subject, crc)
    services.give_consent(enr, signer=crc)
    services.randomize(enr, signer=crc)
    # Baseline 30 days ago → window long closed.
    services.activate(enr, baseline_date=datetime.date.today() - datetime.timedelta(days=30))

    created_first = services.detect_deviations()
    created_second = services.detect_deviations()
    assert created_first == 1
    assert created_second == 0  # no duplicates
    assert Deviation.objects.filter(enrollment=enr).count() == 1


def test_withdraw_closes_enrollment(open_study, subject, crc):
    enr = _enroll(open_study, subject, crc)
    services.withdraw(enr, reason="moved away")
    enr.refresh_from_db()
    assert enr.status == EnrollmentStatus.WITHDRAWN
    with pytest.raises(EnrollmentError):
        services.withdraw(enr, reason="again")
