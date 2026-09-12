from __future__ import annotations

import pytest

from apps.accounts.models import ElectronicSignature, Role
from apps.accounts.services import SignatureError, sign
from apps.audit.models import AuditEvent

pytestmark = pytest.mark.django_db


def test_jwt_login_returns_tokens_and_audits(api, make_user):
    make_user(username="alice", password="Str0ng-Pass-123!", role=Role.CRC)
    resp = api.post(
        "/api/v1/auth/login/",
        {"username": "alice", "password": "Str0ng-Pass-123!"},
        format="json",
    )
    assert resp.status_code == 200
    assert "access" in resp.data and "refresh" in resp.data
    assert AuditEvent.objects.filter(action="LOGIN", actor_label__icontains="alice").exists()


def test_login_with_bad_password_fails(api, make_user):
    make_user(username="bob", password="Str0ng-Pass-123!")
    resp = api.post("/api/v1/auth/login/", {"username": "bob", "password": "wrong"}, format="json")
    assert resp.status_code == 401


def test_me_endpoint_requires_auth(api, crc):
    assert api.get("/api/v1/auth/me/").status_code == 401
    api.force_authenticate(user=crc)
    resp = api.get("/api/v1/auth/me/")
    assert resp.status_code == 200
    assert resp.data["username"] == "crc"
    assert resp.data["role"] == Role.CRC


def test_esignature_requires_reauth(pi):
    pi.set_password("Sign-Me-In-99!")
    pi.save()
    sig = sign(
        signer=pi,
        meaning="Approved",
        entity_type="trials.Study",
        entity_id="1",
        password="Sign-Me-In-99!",
    )
    assert isinstance(sig, ElectronicSignature)
    assert AuditEvent.objects.filter(action="ESIGN", entity_id="1").exists()


def test_esignature_rejects_bad_password(pi):
    pi.set_password("Sign-Me-In-99!")
    pi.save()
    with pytest.raises(SignatureError):
        sign(
            signer=pi,
            meaning="Approved",
            entity_type="trials.Study",
            entity_id="1",
            password="incorrect",
        )
