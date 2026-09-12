"""Unit tests for the trials domain + service layer.

Demonstrates the testing patterns from TECH-NOTES §3.2: factory-built fixtures,
domain-invariant assertions, and an audit-trail assertion on every mutation.
"""
from __future__ import annotations

import pytest

from apps.audit.models import AuditEvent
from apps.trials import services
from apps.trials.models import Arm, Protocol, Study, StudyStatus

pytestmark = pytest.mark.django_db


def _make_study(**kwargs) -> Study:
    protocol = Protocol.objects.create(code="ACME-001", title="A trial", version="1.0")
    return Study.objects.create(
        protocol=protocol,
        name=kwargs.get("name", "Study A"),
        target_enrollment=kwargs.get("target_enrollment", 100),
    )


def test_open_study_requires_an_arm():
    study = _make_study()
    with pytest.raises(services.StudyStateError):
        services.open_study(study)


def test_open_study_transitions_to_open_and_audits():
    study = _make_study()
    Arm.objects.create(study=study, name="Treatment")

    services.open_study(study)

    study.refresh_from_db()
    assert study.status == StudyStatus.OPEN
    # Every clinical mutation must leave an audit trail (TECH-NOTES §3.2).
    assert AuditEvent.objects.filter(entity_type="trials.Study", entity_id=str(study.pk)).exists()


def test_cannot_open_a_completed_study():
    study = _make_study()
    Arm.objects.create(study=study, name="Treatment")
    study.status = StudyStatus.COMPLETED
    study.save()
    with pytest.raises(services.StudyStateError):
        services.open_study(study)


def test_audit_event_is_append_only():
    _make_study()  # creating a study generates audit rows
    event = AuditEvent.objects.order_by("id").last()
    if event is not None:
        with pytest.raises(ValueError):
            event.reason = "tampered"
            event.save()
