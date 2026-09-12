"""Symbolic endpoints: full request → middleware → sandbox → response, plus
the sync-budget escalation flow (202 for authenticated users, 504 anonymous).
"""

import pytest


def test_solve_happy_path(client):
    response = client.post(
        "/api/v1/symbolic/solve",
        json={"expression": "x^2 - 4 = 0", "variable": "x"},
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert sorted(body["solutions"]) == ["-2", "2"]
    assert body["variable"] == "x"
    assert len(body["solutions_latex"]) == 2
    assert body["steps_latex"], "derivation steps must be populated"
    assert body["derivation_latex"].startswith("\\begin{aligned}")
    assert "X-Request-ID" in response.headers


def test_solve_result_is_cached_by_canonical_form(client):
    first = client.post("/api/v1/symbolic/solve", json={"expression": "x^2 - 49 = 0"})
    assert first.status_code == 200 and first.json()["cached"] is False
    # Different text, same canonical equation → cache hit.
    second = client.post("/api/v1/symbolic/solve", json={"expression": "x**2 - 49 = 0"})
    assert second.status_code == 200
    assert second.json()["cached"] is True
    assert sorted(second.json()["solutions"]) == ["-7", "7"]


def test_solve_parse_error_is_problem_json(client):
    response = client.post(
        "/api/v1/symbolic/solve",
        json={"expression": "open('x') + 1", "variable": "x"},
    )
    assert response.status_code == 422
    assert response.headers["content-type"].startswith("application/problem+json")
    body = response.json()
    assert body["status"] == 422
    assert body["request_id"]


def test_solve_dunder_rejected_by_shape_validation(client):
    response = client.post(
        "/api/v1/symbolic/solve",
        json={"expression": "__import__('os')", "variable": "x"},
    )
    assert response.status_code == 422  # pydantic fires before sciengine


class TestCalculusEndpoints:
    def test_differentiate(self, client):
        response = client.post(
            "/api/v1/symbolic/differentiate",
            json={"expression": "x^3", "variable": "x", "order": 1},
        )
        assert response.status_code == 200
        assert response.json()["result_text"] == "3*x**2"

    def test_integrate(self, client):
        response = client.post(
            "/api/v1/symbolic/integrate", json={"expression": "2x", "variable": "x"}
        )
        assert response.status_code == 200
        assert response.json()["result_text"] == "x**2"

    def test_limit(self, client):
        response = client.post(
            "/api/v1/symbolic/limit",
            json={"expression": "sin(x)/x", "variable": "x", "to": "0"},
        )
        assert response.status_code == 200
        assert response.json()["result_text"] == "1"

    def test_series(self, client):
        response = client.post(
            "/api/v1/symbolic/series",
            json={"expression": "cos(x)", "variable": "x", "order": 4},
        )
        assert response.status_code == 200
        assert "x**2" in response.json()["result_text"]

    def test_render(self, client):
        response = client.post("/api/v1/symbolic/render", json={"expression": "sqrt(x)/2"})
        assert response.status_code == 200
        assert response.json()["latex"] == "\\frac{\\sqrt{x}}{2}"


class TestSyncBudgetEscalation:
    """With a near-zero budget every solve times out deterministically,
    exercising the ARCHITECTURE §2.3 fast/heavy routing decision."""

    @pytest.fixture()
    def tiny_budget(self, app_env, monkeypatch):
        monkeypatch.setenv("SYNC_SOLVE_TIMEOUT_SECONDS", "0.0001")
        from tests.conftest import _clear_runtime_caches

        _clear_runtime_caches()
        # Re-create schema caches cleared above? Schema already exists in this DB file.
        yield

    @pytest.fixture()
    def budget_client(self, tiny_budget):
        from fastapi.testclient import TestClient

        from app.main import create_app

        return TestClient(create_app(), raise_server_exceptions=False)

    def test_anonymous_timeout_becomes_504_problem(self, budget_client):
        response = budget_client.post("/api/v1/symbolic/solve", json={"expression": "x^2 - 4 = 0"})
        assert response.status_code == 504
        body = response.json()
        assert body["type"].endswith("computation_timeout")
        assert "background job" in body["detail"] or "Sign in" in body["detail"]

    def test_authenticated_timeout_escalates_to_202_job(self, budget_client):
        email, password = "escalate@example.edu", "hyperbolic-8"
        assert (
            budget_client.post(
                "/api/v1/auth/register", json={"email": email, "password": password}
            ).status_code
            == 201
        )
        tokens = budget_client.post(
            "/api/v1/auth/login", json={"email": email, "password": password}
        ).json()
        headers = {"Authorization": f"Bearer {tokens['access_token']}"}

        response = budget_client.post(
            "/api/v1/symbolic/solve",
            json={"expression": "x^2 - 4 = 0"},
            headers=headers,
        )
        assert response.status_code == 202, response.text
        body = response.json()
        assert body["computation_id"]
        # Eager worker (30s budget) already finished the escalated job.
        poll = budget_client.get(f"/api/v1/computations/{body['computation_id']}", headers=headers)
        assert poll.status_code == 200
        polled = poll.json()
        assert polled["status"] == "succeeded"
        assert sorted(polled["result_payload"]["solutions"]) == ["-2", "2"]


def test_request_id_is_propagated(client):
    response = client.get("/healthz", headers={"X-Request-ID": "test-corr-id-123"})
    assert response.headers["X-Request-ID"] == "test-corr-id-123"
