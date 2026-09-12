"""End-to-end integration flows through the HTTP API.

These exercise the full request cycle (middleware -> view -> service -> DB ->
audit), including actor capture by the AuditContextMiddleware.
"""
from __future__ import annotations

import pytest

from apps.audit.models import AuditEvent
from apps.patients import services as patient_services
from apps.trials.tasks import detect_protocol_deviations

pytestmark = pytest.mark.django_db


def test_full_enrollment_flow_via_api(api, crc):
    api.force_authenticate(user=crc)

    proto = api.post(
        "/api/v1/protocols/",
        {"code": "INT-1", "title": "Integration", "version": "1.0"},
        format="json",
    ).data
    study = api.post(
        "/api/v1/studies/",
        {"protocol": proto["id"], "name": "Int Study", "target_enrollment": 20},
        format="json",
    ).data
    api.post("/api/v1/arms/", {"study": study["id"], "name": "Treatment"}, format="json")
    opened = api.post(f"/api/v1/studies/{study['id']}/open/")
    assert opened.data["status"] == "OPEN"

    subject = patient_services.create_subject(first_name="Int", last_name="Subject")
    enr = api.post(
        "/api/v1/enrollments/",
        {"study": study["id"], "subject": str(subject.public_id)},
        format="json",
    ).data
    assert enr["status"] == "SCREENING"

    eid = enr["id"]
    assert (
        api.post(f"/api/v1/enrollments/{eid}/consent/", {}, format="json").data["status"]
        == "CONSENTED"
    )
    assert (
        api.post(f"/api/v1/enrollments/{eid}/randomize/", {}, format="json").data["status"]
        == "RANDOMIZED"
    )
    activated = api.post(f"/api/v1/enrollments/{eid}/activate/", {}, format="json")
    assert activated.data["status"] == "ENROLLED"

    # The audit trail captured the acting user via middleware.
    assert AuditEvent.objects.filter(
        entity_type="enrollment.Enrollment", actor_label__icontains="crc"
    ).exists()


def test_correlation_id_header_is_returned(api, crc):
    api.force_authenticate(user=crc)
    resp = api.get("/api/v1/studies/")
    assert resp.headers.get("X-Correlation-Id")


def test_eligibility_decision_via_api(api, open_study, subject, pi):
    from apps.eligibility.models import Decision, Screening, ScreeningStatus

    screening = Screening.objects.create(
        study=open_study,
        subject=subject,
        status=ScreeningStatus.AWAITING_REVIEW,
        ml_recommendation=Decision.ELIGIBLE,
    )
    api.force_authenticate(user=pi)
    resp = api.post(
        f"/api/v1/screenings/{screening.public_id}/decision/",
        {"decision": "ELIGIBLE"},
        format="json",
    )
    assert resp.status_code == 200
    assert resp.data["human_decision"] == "ELIGIBLE"


def test_sites_and_delegation_api(api, crc, pi):
    api.force_authenticate(user=crc)
    site = api.post(
        "/api/v1/sites/",
        {"name": "Site 1", "institution": "Hospital", "principal_investigator": pi.pk},
        format="json",
    )
    assert site.status_code == 201
    delegation = api.post(
        "/api/v1/delegations/",
        {
            "site": site.data["id"],
            "user": pi.pk,
            "task": "Obtain consent",
            "granted_on": "2025-01-01",
        },
        format="json",
    )
    assert delegation.status_code == 201
    assert delegation.data["is_active"] is True


def test_notifications_mark_read(api, crc):
    from apps.notifications.models import Notification, NotificationKind

    n = Notification.objects.create(
        recipient=crc, kind=NotificationKind.DEVIATION, subject="s", dedupe_key="d1"
    )
    api.force_authenticate(user=crc)
    resp = api.post(f"/api/v1/notifications/{n.pk}/mark-read/")
    assert resp.status_code == 200
    assert resp.data["read"] is True
    assert api.post("/api/v1/notifications/mark-all-read/").data["marked_read"] == 0


def test_deviation_detection_task_entrypoint():
    result = detect_protocol_deviations()
    assert "deviations_created" in result
