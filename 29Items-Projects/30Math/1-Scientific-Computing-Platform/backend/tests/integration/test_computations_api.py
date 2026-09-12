"""Saved computations: CRUD, the async compute flow (eager worker), artifacts,
and ownership isolation. This is the ARCHITECTURE §2.3 heavy path end-to-end.
"""


def _create(client, headers, kind, input_payload, title="test computation"):
    response = client.post(
        "/api/v1/computations",
        json={"title": title, "kind": kind, "input_payload": input_payload},
        headers=headers,
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_solve_computation_reaches_terminal_success(client, auth_headers):
    body = _create(
        client, auth_headers, "symbolic_solve", {"expression": "x^2 - 9 = 0", "variable": "x"}
    )
    # Celery runs eagerly in tests, so the job is already terminal.
    assert body["status"] == "succeeded"
    assert sorted(body["result_payload"]["solutions"]) == ["-3", "3"]
    assert body["error_code"] is None
    assert body["completed_at"] is not None

    # Poll path (what the SPA does) returns the same record.
    poll = client.get(f"/api/v1/computations/{body['id']}", headers=auth_headers)
    assert poll.status_code == 200
    assert poll.json()["status"] == "succeeded"


def test_integral_computation(client, auth_headers):
    body = _create(client, auth_headers, "integral", {"expression": "2x", "variable": "x"})
    assert body["status"] == "succeeded"
    assert body["result_payload"]["result_text"] == "x**2"


def test_ode_computation_returns_sampled_solution(client, auth_headers):
    body = _create(
        client,
        auth_headers,
        "ode",
        {"expression": "-y", "t_start": 0.0, "t_end": 1.0, "y0": 1.0, "n_points": 50},
    )
    assert body["status"] == "succeeded"
    result = body["result_payload"]
    assert len(result["t"]) == len(result["y"]) == 50
    assert abs(result["y"][-1] - 0.3678794) < 1e-3  # e^{-1}


def test_ml_classify_computation(client, auth_headers):
    body = _create(client, auth_headers, "ml_classify", {"expression": "3x^2 + 1"})
    assert body["status"] == "succeeded"
    assert body["result_payload"]["label"] == "quadratic"
    assert body["result_payload"]["source"] == "heuristic"


def test_plot_computation_stores_and_serves_artifact(client, auth_headers):
    body = _create(
        client,
        auth_headers,
        "plot",
        {"expression": "sin(x)", "x_min": -6.28, "x_max": 6.28},
    )
    assert body["status"] == "succeeded"
    assert body["result_payload"]["artifact"]["storage"] == "local"

    artifact = client.get(f"/api/v1/computations/{body['id']}/artifact", headers=auth_headers)
    assert artifact.status_code == 200
    assert artifact.headers["content-type"].startswith("image/svg+xml")
    assert b"<svg" in artifact.content


def test_failed_computation_reaches_terminal_failed_state(client, auth_headers):
    # 'y' never appears → UnsupportedExpressionError inside the worker.
    body = _create(
        client, auth_headers, "symbolic_solve", {"expression": "1 + 2 = 0", "variable": "x"}
    )
    assert body["status"] == "failed"
    assert body["error_code"] == "unsupported_expression"
    assert "error" in body["result_payload"]


def test_invalid_payload_for_kind_rejected_at_the_boundary(client, auth_headers):
    response = client.post(
        "/api/v1/computations",
        json={
            "title": "bad",
            "kind": "ode",
            "input_payload": {"expression": "-y", "t_start": 5, "t_end": 1},
        },
        headers=auth_headers,
    )
    assert response.status_code == 422


def test_list_pagination_and_delete(client, auth_headers):
    for i in range(3):
        _create(
            client,
            auth_headers,
            "ml_classify",
            {"expression": f"{i + 2}x + 1"},
            title=f"job {i}",
        )
    page = client.get("/api/v1/computations?limit=2&offset=0", headers=auth_headers)
    assert page.status_code == 200
    body = page.json()
    assert body["total"] == 3
    assert len(body["items"]) == 2

    victim = body["items"][0]["id"]
    assert client.delete(f"/api/v1/computations/{victim}", headers=auth_headers).status_code == 204
    assert client.get(f"/api/v1/computations/{victim}", headers=auth_headers).status_code == 404


def test_ownership_isolation(client, user_factory):
    alice, bob = user_factory(), user_factory()
    body = _create(client, alice["headers"], "ml_classify", {"expression": "x + 1"})
    # Bob cannot read or delete Alice's computation — 404, not 403 (no leak).
    assert (
        client.get(f"/api/v1/computations/{body['id']}", headers=bob["headers"]).status_code == 404
    )
    assert (
        client.delete(f"/api/v1/computations/{body['id']}", headers=bob["headers"]).status_code
        == 404
    )


def test_requires_auth(client):
    assert client.get("/api/v1/computations").status_code == 401
    assert (
        client.post(
            "/api/v1/computations",
            json={"title": "x", "kind": "ml_classify", "input_payload": {"expression": "x"}},
        ).status_code
        == 401
    )
