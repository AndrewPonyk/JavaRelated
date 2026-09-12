"""Account/auth tests: registration, JWT login, profile, logout, role rules."""

import pytest
from django.contrib.auth import get_user_model

pytestmark = pytest.mark.django_db
User = get_user_model()


def test_register_creates_customer(api_client):
    resp = api_client.post(
        "/api/v1/auth/register/",
        {"email": "New@Example.com", "password": "s3cret-pass-99"},
        format="json",
    )
    assert resp.status_code == 201
    user = User.objects.get(email="new@example.com")
    assert user.role == User.Role.CUSTOMER
    assert user.check_password("s3cret-pass-99")


def test_register_rejects_staff_role(api_client):
    resp = api_client.post(
        "/api/v1/auth/register/",
        {"email": "x@example.com", "password": "s3cret-pass-99", "role": "staff"},
        format="json",
    )
    assert resp.status_code == 400
    assert "role" in resp.data["error"]["details"]


def test_register_duplicate_email(api_client, user):
    resp = api_client.post(
        "/api/v1/auth/register/",
        {"email": user.email, "password": "s3cret-pass-99"},
        format="json",
    )
    assert resp.status_code == 400


def test_login_returns_tokens_and_user(api_client, user_factory):
    user_factory(email="login@example.com", password="my-password-123")
    resp = api_client.post(
        "/api/v1/auth/token/",
        {"email": "login@example.com", "password": "my-password-123"},
        format="json",
    )
    assert resp.status_code == 200
    assert "access" in resp.data and "refresh" in resp.data
    assert resp.data["user"]["email"] == "login@example.com"


def test_me_requires_auth(api_client):
    assert api_client.get("/api/v1/auth/me/").status_code == 401


def test_me_returns_profile(auth_client, user):
    resp = auth_client.get("/api/v1/auth/me/")
    assert resp.status_code == 200
    assert resp.data["email"] == user.email


def test_logout_blacklists_refresh(api_client, user_factory):
    user_factory(email="lo@example.com", password="my-password-123")
    tokens = api_client.post(
        "/api/v1/auth/token/",
        {"email": "lo@example.com", "password": "my-password-123"},
        format="json",
    ).data
    api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {tokens['access']}")
    resp = api_client.post("/api/v1/auth/logout/", {"refresh": tokens["refresh"]}, format="json")
    assert resp.status_code == 205
    # The blacklisted refresh token can no longer be used.
    refresh_resp = api_client.post(
        "/api/v1/auth/token/refresh/", {"refresh": tokens["refresh"]}, format="json"
    )
    assert refresh_resp.status_code == 401


def test_create_superuser_by_email():
    su = User.objects.create_superuser(email="boss@example.com", password="x")
    assert su.is_staff and su.is_superuser and su.role == User.Role.STAFF
