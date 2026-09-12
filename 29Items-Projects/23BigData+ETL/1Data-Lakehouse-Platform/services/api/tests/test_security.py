"""JWT authentication and role-based authorization.

Enforced mode is activated by setting AUTH_JWKS_URL; the JWKS fetch is patched
so tokens sign/verify against a locally generated RSA keypair — no IdP needed.
"""

from __future__ import annotations

import time
from types import SimpleNamespace

import jwt as pyjwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

AUDIENCE = "lakehouse-console"

DATASET = {
    "name": "sales.secure",
    "layer": "gold",
    "owner_email": "eng@example.com",
    "s3_path": "s3://bucket/sales/secure",
}


@pytest.fixture(scope="module")
def rsa_key():
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


def make_token(
    key,
    *,
    sub: str = "alice@example.com",
    roles: list[str] | str | None = None,
    aud: str = AUDIENCE,
    expires_in: int = 3600,
    roles_claim: str = "roles",
) -> str:
    now = int(time.time())
    payload = {"sub": sub, "aud": aud, "iat": now, "exp": now + expires_in}
    if roles is not None:
        payload[roles_claim] = roles
    return pyjwt.encode(payload, key, algorithm="RS256", headers={"kid": "test-key"})


@pytest.fixture()
def enforced_client(monkeypatch, make_client, rsa_key):
    """Client with auth enforced and the JWKS lookup patched to our public key."""
    monkeypatch.setenv("AUTH_JWKS_URL", "https://idp.example.com/.well-known/jwks.json")
    public_key = rsa_key.public_key()
    monkeypatch.setattr(
        pyjwt.PyJWKClient,
        "get_signing_key_from_jwt",
        lambda self, token: SimpleNamespace(key=public_key),
    )
    return make_client()


def _bearer(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def test_missing_token_is_401(enforced_client):
    response = enforced_client.get("/api/v1/datasets")
    assert response.status_code == 401
    assert response.headers.get("www-authenticate") == "Bearer"


def test_garbage_token_is_401(enforced_client):
    assert enforced_client.get("/api/v1/datasets", headers=_bearer("not.a.jwt")).status_code == 401


def test_expired_token_is_401(enforced_client, rsa_key):
    token = make_token(rsa_key, expires_in=-60)
    response = enforced_client.get("/api/v1/datasets", headers=_bearer(token))
    assert response.status_code == 401
    assert "expired" in response.json()["detail"].lower()


def test_wrong_audience_is_401(enforced_client, rsa_key):
    token = make_token(rsa_key, aud="some-other-app")
    assert enforced_client.get("/api/v1/datasets", headers=_bearer(token)).status_code == 401


def test_analyst_can_read_but_not_write(enforced_client, rsa_key):
    token = make_token(rsa_key, roles=["analyst"])
    assert enforced_client.get("/api/v1/datasets", headers=_bearer(token)).status_code == 200

    response = enforced_client.post("/api/v1/datasets", json=DATASET, headers=_bearer(token))
    assert response.status_code == 403
    assert "platform-admin" in response.json()["detail"]


def test_data_engineer_can_write_and_actor_is_audited(enforced_client, rsa_key):
    token = make_token(rsa_key, sub="eng@example.com", roles=["data-engineer"])
    created = enforced_client.post("/api/v1/datasets", json=DATASET, headers=_bearer(token))
    assert created.status_code == 201, created.text

    audit = enforced_client.get(
        "/api/v1/audit", params={"entity_id": created.json()["id"]}, headers=_bearer(token)
    ).json()
    assert audit["items"][0]["actor"] == "eng@example.com"


def test_space_separated_roles_claim_is_supported(enforced_client, rsa_key):
    token = make_token(rsa_key, roles="viewer data-engineer")
    response = enforced_client.post("/api/v1/datasets", json=DATASET, headers=_bearer(token))
    assert response.status_code == 201


def test_token_without_roles_gets_403_on_write(enforced_client, rsa_key):
    token = make_token(rsa_key)  # no roles claim at all
    assert (
        enforced_client.post("/api/v1/datasets", json=DATASET, headers=_bearer(token)).status_code
        == 403
    )


def test_idp_unreachable_is_503_not_401(enforced_client, rsa_key, monkeypatch):
    """The IdP being down is a service outage, not the caller's credential problem."""

    def raise_connection_error(self, token):
        raise pyjwt.PyJWKClientConnectionError("connection refused")

    monkeypatch.setattr(pyjwt.PyJWKClient, "get_signing_key_from_jwt", raise_connection_error)
    response = enforced_client.get(
        "/api/v1/datasets", headers=_bearer(make_token(rsa_key, roles=["analyst"]))
    )
    assert response.status_code == 503
    assert "unavailable" in response.json()["detail"].lower()


def test_dev_mode_without_jwks_acts_as_admin(client):
    # No AUTH_JWKS_URL → documented development mode
    assert client.get("/api/v1/datasets").status_code == 200
    assert client.post("/api/v1/datasets", json=DATASET).status_code == 201
