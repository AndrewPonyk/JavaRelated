"""Vendor onboarding API tests."""

import pytest

from apps.accounts.models import User
from apps.vendors.models import Vendor

pytestmark = pytest.mark.django_db


def test_public_list_shows_only_approved(api_client, vendor_factory):
    vendor_factory(status=Vendor.Status.APPROVED)
    vendor_factory(status=Vendor.Status.PENDING)
    resp = api_client.get("/api/v1/vendors/")
    assert resp.status_code == 200
    count = resp.data["count"] if isinstance(resp.data, dict) else len(resp.data)
    assert count == 1


def test_create_vendor_profile_promotes_user(auth_client, user):
    resp = auth_client.post("/api/v1/vendors/", {"name": "Acme Co"}, format="json")
    assert resp.status_code == 201
    user.refresh_from_db()
    assert user.role == User.Role.VENDOR
    assert Vendor.objects.filter(owner=user).exists()


def test_cannot_create_two_profiles(auth_client, user, vendor_factory):
    vendor_factory(owner=user)
    resp = auth_client.post("/api/v1/vendors/", {"name": "Second"}, format="json")
    assert resp.status_code == 409


def test_vendor_me_endpoint(auth_client, user, vendor_factory):
    vendor_factory(owner=user, name="Mine")
    resp = auth_client.get("/api/v1/vendors/me/")
    assert resp.status_code == 200
    assert resp.data["name"] == "Mine"


def test_vendor_me_when_none(auth_client):
    assert auth_client.get("/api/v1/vendors/me/").status_code == 404


def test_create_requires_auth(api_client):
    assert api_client.post("/api/v1/vendors/", {"name": "X"}, format="json").status_code == 401
