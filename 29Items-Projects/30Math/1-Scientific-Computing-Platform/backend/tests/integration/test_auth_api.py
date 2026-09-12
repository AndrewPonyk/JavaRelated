"""Auth flow: register → login → me → refresh rotation → logout revocation."""


def test_register_login_me_roundtrip(client):
    email = "ada@example.edu"
    register = client.post(
        "/api/v1/auth/register", json={"email": email, "password": "hyperbolic-8"}
    )
    assert register.status_code == 201
    body = register.json()
    assert body["email"] == email
    assert body["role"] == "student"

    login = client.post("/api/v1/auth/login", json={"email": email, "password": "hyperbolic-8"})
    assert login.status_code == 200
    tokens = login.json()
    assert tokens["token_type"] == "bearer"
    assert tokens["expires_in"] > 0

    me = client.get(
        "/api/v1/auth/me", headers={"Authorization": f"Bearer {tokens['access_token']}"}
    )
    assert me.status_code == 200
    assert me.json()["email"] == email


def test_duplicate_email_conflicts(client):
    payload = {"email": "dup@example.edu", "password": "hyperbolic-8"}
    assert client.post("/api/v1/auth/register", json=payload).status_code == 201
    assert client.post("/api/v1/auth/register", json=payload).status_code == 409


def test_wrong_password_rejected_identically_to_unknown_user(client):
    client.post(
        "/api/v1/auth/register", json={"email": "eve@example.edu", "password": "hyperbolic-8"}
    )
    wrong = client.post(
        "/api/v1/auth/login", json={"email": "eve@example.edu", "password": "wrong-wrong-1"}
    )
    unknown = client.post(
        "/api/v1/auth/login", json={"email": "ghost@example.edu", "password": "whatever-123"}
    )
    assert wrong.status_code == unknown.status_code == 401
    assert wrong.json()["detail"] == unknown.json()["detail"]  # no enumeration signal


def test_password_policy_enforced(client):
    response = client.post(
        "/api/v1/auth/register", json={"email": "short@example.edu", "password": "short"}
    )
    assert response.status_code == 422


def test_refresh_rotation_revokes_presented_token(user_factory, client):
    tokens = user_factory()["tokens"]

    first_refresh = client.post(
        "/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]}
    )
    assert first_refresh.status_code == 200
    new_tokens = first_refresh.json()
    assert new_tokens["refresh_token"] != tokens["refresh_token"]

    # The old refresh token was rotated out — replaying it must fail.
    replay = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert replay.status_code == 401

    # The rotated-in token works.
    second_refresh = client.post(
        "/api/v1/auth/refresh", json={"refresh_token": new_tokens["refresh_token"]}
    )
    assert second_refresh.status_code == 200


def test_logout_revokes_refresh_token(user_factory, client):
    tokens = user_factory()["tokens"]
    assert (
        client.post(
            "/api/v1/auth/logout", json={"refresh_token": tokens["refresh_token"]}
        ).status_code
        == 204
    )
    after = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert after.status_code == 401


def test_access_token_cannot_be_used_as_refresh(user_factory, client):
    tokens = user_factory()["tokens"]
    response = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["access_token"]})
    assert response.status_code == 401
