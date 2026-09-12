"""Audit trail tests — 21 CFR Part 11 critical paths (target 100%)."""
from __future__ import annotations

import pytest

from apps.audit.models import AuditAction, AuditEvent
from apps.audit.tasks import verify_audit_integrity
from apps.patients import services as patient_services
from apps.trials.models import Protocol

pytestmark = pytest.mark.django_db


def test_create_writes_audit_event(protocol):
    event = AuditEvent.objects.filter(entity_type="trials.Protocol").latest("id")
    assert event.action == AuditAction.CREATE
    assert event.entity_id == str(protocol.pk)


def test_update_records_field_diff(study):
    study.target_enrollment = 250
    study.save()
    event = AuditEvent.objects.filter(entity_type="trials.Study", action=AuditAction.UPDATE).latest(
        "id"
    )
    assert event.changes["target_enrollment"] == [100, 250]


def test_phi_is_redacted_in_audit_trail():
    subject = patient_services.create_subject(first_name="Jane", last_name="Doe")
    subject.first_name = "Janet"
    subject.save()
    event = AuditEvent.objects.filter(entity_type="patients.Subject", action="UPDATE").latest("id")
    # The fact that first_name changed is recorded, but never the values.
    assert event.changes["first_name"] == ["***", "***"]


def test_audit_event_is_append_only(protocol):
    event = AuditEvent.objects.latest("id")
    with pytest.raises(ValueError):
        event.reason = "tampered"
        event.save()
    with pytest.raises(ValueError):
        event.delete()


def test_hash_chain_links_previous_row():
    Protocol.objects.create(code="P1", title="t1", version="1.0")
    Protocol.objects.create(code="P2", title="t2", version="1.0")
    events = list(AuditEvent.objects.order_by("id"))
    assert events[-1].prev_hash == events[-2].row_hash


def test_integrity_check_passes_for_untampered_chain(study):
    result = verify_audit_integrity()
    assert result["broken_count"] == 0


def test_integrity_check_detects_tampering(study):
    # Bypass the append-only save() guard via an UPDATE query to simulate tampering.
    target = AuditEvent.objects.latest("id")
    AuditEvent.objects.filter(pk=target.pk).update(reason="covertly altered")
    result = verify_audit_integrity()
    assert result["broken_count"] >= 1
