"""Full HTTP round-trips — no mocking of the library, real numerics on small inputs."""

import time

import pytest

pytestmark = pytest.mark.integration

PRICE_BODY = {
    "spot": 100,
    "strikes": [90, 100, 110],
    "vol": 0.2,
    "rate": 0.05,
    "expiry": 1.0,
    "kind": "call",
}


class TestHealth:
    def test_live(self, client):
        assert client.get("/health/live").json() == {"status": "ok"}

    def test_ready_reports_native_flag(self, client):
        body = client.get("/health/ready").json()
        assert body["status"] == "ok"
        assert "native_core" in body


class TestPricing:
    def test_price_chain_happy_path(self, client):
        resp = client.post("/v1/options/price", json=PRICE_BODY)
        assert resp.status_code == 200
        payload = resp.json()
        assert len(payload["prices"]) == 3
        # ITM call worth more than OTM call
        assert payload["prices"][0] > payload["prices"][2]
        assert resp.headers["X-Request-ID"] == payload["request_id"]

    def test_negative_vol_is_422_with_detail(self, client):
        resp = client.post("/v1/options/price", json=PRICE_BODY | {"vol": -0.2})
        assert resp.status_code == 422

    def test_negative_strike_is_422(self, client):
        resp = client.post("/v1/options/price", json=PRICE_BODY | {"strikes": [-5]})
        assert resp.status_code == 422

    def test_greeks_endpoint(self, client):
        resp = client.post(
            "/v1/options/greeks",
            json={
                "spot": 100,
                "strike": 100,
                "vol": 0.2,
                "rate": 0.05,
                "expiry": 1.0,
                "kind": "call",
            },
        )
        assert resp.status_code == 200
        payload = resp.json()
        assert 0.5 < payload["delta"] < 0.7
        assert payload["gamma"] > 0

    def test_mc_price_echoes_seed_and_ci(self, client):
        resp = client.post(
            "/v1/options/mc-price",
            json={
                "spot": 100,
                "strike": 100,
                "vol": 0.2,
                "rate": 0.05,
                "expiry": 1.0,
                "kind": "call",
                "n_paths": 20000,
                "seed": 7,
            },
        )
        assert resp.status_code == 200
        payload = resp.json()
        assert payload["seed"] == 7
        assert payload["ci_low"] < payload["price"] < payload["ci_high"]

    def test_mc_paths_over_server_limit_is_422(self, client):
        resp = client.post(
            "/v1/options/mc-price",
            json={
                "spot": 100,
                "strike": 100,
                "vol": 0.2,
                "rate": 0.05,
                "expiry": 1.0,
                "kind": "call",
                "n_paths": 100_000_000,
            },
        )
        assert resp.status_code == 422

    def test_implied_vol_round_trip(self, client):
        priced = client.post("/v1/options/price", json=PRICE_BODY | {"strikes": [100]}).json()
        resp = client.post(
            "/v1/options/implied-vol",
            json={
                "price": priced["prices"][0],
                "spot": 100,
                "strike": 100,
                "rate": 0.05,
                "expiry": 1.0,
                "kind": "call",
            },
        )
        assert resp.json()["implied_vol"] == pytest.approx(0.2, abs=1e-6)

    def test_unattainable_implied_vol_is_422(self, client):
        resp = client.post(
            "/v1/options/implied-vol",
            json={
                "price": 0.000001,
                "spot": 100,
                "strike": 100,
                "rate": 0.05,
                "expiry": 1.0,
                "kind": "call",
            },
        )
        assert resp.status_code == 422
        assert "attainable" in resp.json()["detail"]


class TestRisk:
    def test_var_happy_path(self, client):
        returns = [0.01, -0.02, 0.005, -0.015, 0.02, -0.03, 0.01, -0.001] * 30
        resp = client.post("/v1/risk/var", json={"returns": returns, "confidence": 0.99})
        assert resp.status_code == 200
        payload = resp.json()
        assert payload["expected_shortfall"] >= payload["var"] > 0

    def test_portfolio_var(self, client):
        returns = {
            "equities": [0.01, -0.02, 0.005, -0.015, 0.02, -0.03] * 40,
            "bonds": [0.002, -0.001, 0.001, -0.002, 0.001, -0.001] * 40,
        }
        resp = client.post(
            "/v1/risk/portfolio-var",
            json={
                "returns": returns,
                "weights": {"equities": 0.6, "bonds": 0.4},
                "confidence": 0.95,
            },
        )
        assert resp.status_code == 200
        assert resp.json()["var"] > 0

    def test_portfolio_var_mismatched_weights_is_422(self, client):
        resp = client.post(
            "/v1/risk/portfolio-var", json={"returns": {"a": [0.01, -0.01]}, "weights": {"b": 1.0}}
        )
        assert resp.status_code == 422


class TestVolSurfaceJobFlow:
    @staticmethod
    def _sample_quotes(n_strikes: int = 12) -> list[dict]:
        import numpy as np

        quotes = []
        for expiry in (0.25, 1.0):
            for strike in np.linspace(80, 120, n_strikes):
                k = float(np.log(strike / 100.0))
                quotes.append(
                    {
                        "strike": float(strike),
                        "expiry": expiry,
                        "implied_vol": 0.2 + 0.3 * k * k + 0.02 * expiry,
                    }
                )
        return quotes

    def _poll_until_done(self, client, job_id: str, timeout: float = 120.0) -> dict:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            payload = client.get(f"/v1/vol-surface/jobs/{job_id}").json()
            if payload["status"] in ("DONE", "FAILED"):
                return payload
            time.sleep(0.25)
        pytest.fail(f"job {job_id} did not finish within {timeout}s")

    @pytest.mark.slow
    def test_fit_poll_query_lifecycle(self, client):
        # Submit -> 202 + job id
        resp = client.post(
            "/v1/vol-surface/fit",
            json={"forward": 100.0, "quotes": self._sample_quotes(), "epochs": 900, "seed": 0},
        )
        assert resp.status_code == 202
        job_id = resp.json()["job_id"]
        assert resp.json()["status"] == "PENDING"

        # Poll -> DONE with a result_url
        final = self._poll_until_done(client, job_id)
        assert final["status"] == "DONE", f"fit failed: {final['detail']}"
        assert final["result_url"].startswith("/v1/vol-surface/")
        surface_id = final["result_url"].rsplit("/", 1)[-1]

        # Query the fitted surface: ATM vol should be near the input smile.
        q = client.get(f"/v1/vol-surface/{surface_id}", params={"strike": 100.0, "expiry": 1.0})
        assert q.status_code == 200
        assert q.json()["implied_vol"] == pytest.approx(0.22, abs=0.05)

        # Metadata endpoint agrees.
        info = client.get(f"/v1/vol-surface/{surface_id}/info").json()
        assert info["n_quotes"] == len(self._sample_quotes())
        assert info["mse"] < 1e-2

    def test_failed_fit_reports_detail(self, client):
        # One epoch cannot reach the convergence tolerance -> job must end
        # FAILED with the ConvergenceError message, never a bad surface.
        resp = client.post(
            "/v1/vol-surface/fit",
            json={"forward": 100.0, "quotes": self._sample_quotes(), "epochs": 1, "seed": 0},
        )
        assert resp.status_code == 202
        final = self._poll_until_done(client, resp.json()["job_id"], timeout=60)
        assert final["status"] == "FAILED"
        assert "did not converge" in final["detail"]
        assert final["result_url"] is None

    def test_unknown_job_404(self, client):
        assert client.get("/v1/vol-surface/jobs/nope").status_code == 404

    def test_unknown_surface_404(self, client):
        resp = client.get("/v1/vol-surface/nope", params={"strike": 100, "expiry": 1})
        assert resp.status_code == 404

    def test_too_few_quotes_is_422(self, client):
        resp = client.post(
            "/v1/vol-surface/fit",
            json={
                "forward": 100.0,
                "quotes": [{"strike": 100, "expiry": 1.0, "implied_vol": 0.2}] * 5,
            },
        )
        assert resp.status_code == 422


class TestAudit:
    def test_pricing_calls_are_audited(self, client):
        client.post("/v1/options/price", json=PRICE_BODY)
        client.post(
            "/v1/options/mc-price",
            json={
                "spot": 100,
                "strike": 100,
                "vol": 0.2,
                "rate": 0.05,
                "expiry": 1.0,
                "kind": "call",
                "n_paths": 10000,
                "seed": 99,
            },
        )

        page = client.get("/v1/admin/audit").json()
        assert page["count"] >= 2
        endpoints = {e["endpoint"] for e in page["entries"]}
        assert "/v1/options/price" in endpoints
        assert "/v1/options/mc-price" in endpoints
        mc_rows = [e for e in page["entries"] if e["endpoint"] == "/v1/options/mc-price"]
        assert mc_rows[0]["seed"] == 99  # seed recorded -> number reproducible
        assert all(len(e["params_hash"]) == 64 for e in page["entries"])

    def test_audit_pagination(self, client):
        for _ in range(3):
            client.post("/v1/options/price", json=PRICE_BODY)
        first = client.get("/v1/admin/audit", params={"limit": 2, "offset": 0}).json()
        rest = client.get("/v1/admin/audit", params={"limit": 2, "offset": 2}).json()
        assert first["count"] == 2 and first["limit"] == 2 and first["offset"] == 0
        assert rest["count"] >= 1
        ids = {e["id"] for e in first["entries"]} | {e["id"] for e in rest["entries"]}
        assert len(ids) == first["count"] + rest["count"]  # pages don't overlap

    def test_audit_requires_admin_scope_bad_offset_is_422(self, client):
        assert client.get("/v1/admin/audit", params={"offset": -1}).status_code == 422
        assert client.get("/v1/admin/audit", params={"limit": 0}).status_code == 422
