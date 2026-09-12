from __future__ import annotations

import datetime

import pytest

from apps.enrollment import services as enroll_services
from apps.notifications.models import Notification, NotificationKind
from apps.notifications.services import dispatch
from apps.notifications.tasks import send_visit_reminders
from apps.trials.models import VisitTemplate

pytestmark = pytest.mark.django_db


def test_dispatch_is_idempotent(crc):
    first = dispatch(recipient=crc, kind=NotificationKind.DEVIATION, subject="x", dedupe_key="k1")
    second = dispatch(recipient=crc, kind=NotificationKind.DEVIATION, subject="x", dedupe_key="k1")
    assert first is not None
    assert second is None
    assert Notification.objects.filter(dedupe_key="k1").count() == 1


def test_visit_reminders_notify_overdue(open_study, subject, crc):
    VisitTemplate.objects.create(
        study=open_study, name="Baseline", day_offset=0, window_after_days=3
    )
    enr = enroll_services.create_enrollment(study=open_study, subject=subject, created_by=crc)
    enroll_services.give_consent(enr, signer=crc)
    enroll_services.randomize(enr, signer=crc)
    enroll_services.activate(enr, baseline_date=datetime.date.today() - datetime.timedelta(days=10))

    result = send_visit_reminders()
    assert result["sent"] == 1
    assert Notification.objects.filter(recipient=crc, kind=NotificationKind.VISIT_OVERDUE).exists()
    # Idempotent across re-runs.
    assert send_visit_reminders()["sent"] == 0


def test_notification_subject_uses_subject_code_not_phi(open_study, subject, crc):
    VisitTemplate.objects.create(
        study=open_study, name="Baseline", day_offset=0, window_after_days=3
    )
    enr = enroll_services.create_enrollment(study=open_study, subject=subject, created_by=crc)
    enroll_services.give_consent(enr, signer=crc)
    enroll_services.randomize(enr, signer=crc)
    enroll_services.activate(enr, baseline_date=datetime.date.today() - datetime.timedelta(days=10))
    send_visit_reminders()
    note = Notification.objects.filter(recipient=crc).first()
    assert subject.subject_code in note.subject
    assert "Jane" not in note.subject  # no PHI in the message
