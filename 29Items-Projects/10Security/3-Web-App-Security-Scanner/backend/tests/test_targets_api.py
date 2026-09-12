"""API tests: scan-target allowlist CRUD."""

from __future__ import annotations

from tests.conftest import auth_headers, make_user


async def test_crud_roundtrip(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    headers = auth_headers(admin)

    created = await client.post(
        "/api/v1/targets",
        headers=headers,
        json={
            "host_pattern": "APP.Example.COM.",  # normalized: lowercase, trailing dot stripped
            "description": "main app",
        },
    )
    assert created.status_code == 201
    assert created.json()["host_pattern"] == "app.example.com"
    assert created.json()["verified_at"] is not None
    target_id = created.json()["id"]

    listed = await client.get("/api/v1/targets", headers=headers)
    assert [t["id"] for t in listed.json()] == [target_id]

    verified = await client.post(f"/api/v1/targets/{target_id}/verify", headers=headers)
    assert verified.status_code == 200

    deleted = await client.delete(f"/api/v1/targets/{target_id}", headers=headers)
    assert deleted.status_code == 204
    assert (await client.get("/api/v1/targets", headers=headers)).json() == []


async def test_cidr_pattern_accepted(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    resp = await client.post(
        "/api/v1/targets",
        headers=auth_headers(admin),
        json={
            "host_pattern": "10.0.4.0/24",
        },
    )
    assert resp.status_code == 201


async def test_duplicate_pattern_409(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    headers = auth_headers(admin)
    body = {"host_pattern": "app.example.com"}
    assert (await client.post("/api/v1/targets", headers=headers, json=body)).status_code == 201
    resp = await client.post("/api/v1/targets", headers=headers, json=body)
    assert resp.status_code == 409


async def test_invalid_patterns_422(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    headers = auth_headers(admin)
    for bad in ("https://app.example.com/path", "has space.com", "x", "bad_host!"):
        resp = await client.post(
            "/api/v1/targets",
            headers=headers,
            json={
                "host_pattern": bad,
            },
        )
        assert resp.status_code == 422, bad


async def test_rbac_viewer_cannot_write(client, session_factory):
    viewer = await make_user(session_factory, "v@test.dev", "viewer")
    assert (
        await client.post(
            "/api/v1/targets",
            headers=auth_headers(viewer),
            json={
                "host_pattern": "app.example.com",
            },
        )
    ).status_code == 403
    assert (
        await client.delete("/api/v1/targets/1", headers=auth_headers(viewer))
    ).status_code == 403
    # read is allowed
    assert (await client.get("/api/v1/targets", headers=auth_headers(viewer))).status_code == 200


async def test_delete_missing_404(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    resp = await client.delete("/api/v1/targets/999", headers=auth_headers(admin))
    assert resp.status_code == 404
