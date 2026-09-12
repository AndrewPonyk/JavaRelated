"""Meta endpoints: health check, OpenAPI schema, Swagger UI, gzip."""

import pytest

pytestmark = pytest.mark.django_db


def test_healthz(client):
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_openapi_schema_generates(client):
    resp = client.get("/api/schema/")
    assert resp.status_code == 200
    body = resp.content.decode()
    # Core paths are present in the generated spec.
    assert "/api/v1/catalog/products/" in body
    assert "/api/v1/orders/checkout/" in body


def test_swagger_ui_served(client):
    resp = client.get("/api/docs/")
    assert resp.status_code == 200


def test_argon2_is_default_hasher(settings):
    from django.contrib.auth.hashers import get_hasher

    # Production/base uses Argon2 (test settings override with MD5 for speed).
    settings.PASSWORD_HASHERS = [
        "django.contrib.auth.hashers.Argon2PasswordHasher",
        "django.contrib.auth.hashers.PBKDF2PasswordHasher",
    ]
    assert get_hasher("default").algorithm == "argon2"
