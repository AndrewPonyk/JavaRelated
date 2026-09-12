"""Trials API: CRUD, state-transition actions, and the RBAC permission matrix."""
from __future__ import annotations

import pytest

from apps.trials.models import Arm

pytestmark = pytest.mark.django_db


def test_create_protocol_then_study(api, crc):
    api.force_authenticate(user=crc)
    p = api.post(
        "/api/v1/protocols/",
        {"code": "X-1", "title": "Trial X", "version": "1.0", "phase": "II"},
        format="json",
    )
    assert p.status_code == 201
    s = api.post(
        "/api/v1/studies/",
        {"protocol": p.data["id"], "name": "Study X", "target_enrollment": 50},
        format="json",
    )
    assert s.status_code == 201
    assert s.data["status"] == "DRAFT"


def test_open_study_action(api, study, crc):
    Arm.objects.create(study=study, name="T")
    api.force_authenticate(user=crc)
    resp = api.post(f"/api/v1/studies/{study.public_id}/open/")
    assert resp.status_code == 200
    assert resp.data["status"] == "OPEN"


def test_open_without_arm_conflicts(api, study, crc):
    api.force_authenticate(user=crc)
    resp = api.post(f"/api/v1/studies/{study.public_id}/open/")
    assert resp.status_code == 409
    assert (
        resp.data["error"]["code"] == "ENROLLMENT_STATE_INVALID"
        or resp.data["error"]["code"] == "CONFLICT"
    )


def test_duplicate_protocol_version_rejected(api, protocol, crc):
    api.force_authenticate(user=crc)
    resp = api.post(
        "/api/v1/protocols/",
        {"code": protocol.code, "title": "dupe", "version": protocol.version},
        format="json",
    )
    assert resp.status_code == 400


def test_progress_action(api, open_study, crc):
    api.force_authenticate(user=crc)
    resp = api.get(f"/api/v1/studies/{open_study.public_id}/progress/")
    assert resp.status_code == 200
    assert resp.data == {"enrolled": 0, "target": 100, "percent": 0.0}


@pytest.mark.parametrize(
    "role_fixture,expected",
    [("crc", 201), ("pi", 201), ("auditor", 403), ("monitor", 403)],
)
def test_write_permission_matrix(api, request, protocol, role_fixture, expected):
    user = request.getfixturevalue(role_fixture)
    api.force_authenticate(user=user)
    resp = api.post(
        "/api/v1/studies/",
        {"protocol": str(protocol.public_id), "name": "RBAC", "target_enrollment": 10},
        format="json",
    )
    assert resp.status_code == expected


def test_read_allowed_for_all_roles(api, study, auditor, monitor):
    for user in (auditor, monitor):
        api.force_authenticate(user=user)
        assert api.get("/api/v1/studies/").status_code == 200


def test_unauthenticated_is_rejected(api):
    resp = api.get("/api/v1/studies/")
    assert resp.status_code == 401
    # Error envelope surfaces the real message + a correlation id (ARCHITECTURE §2.6).
    assert resp.data["error"]["code"] == "NOT_AUTHENTICATED"
    assert "credentials" in resp.data["error"]["message"].lower()
    assert resp.data["error"]["correlation_id"]
