"""Authentication, scopes, and rate limiting."""

import pytest
from conftest import ADMIN_KEY, ALL_KEY, FIT_KEY, PRICE_KEY

pytestmark = pytest.mark.integration

PRICE_BODY = {
    "spot": 100,
    "strikes": [100],
    "vol": 0.2,
    "rate": 0.05,
    "expiry": 1.0,
    "kind": "call",
}


class TestApiKeys:
    def test_missing_key_is_401(self, keyed_client):
        resp = keyed_client.post("/v1/options/price", json=PRICE_BODY)
        assert resp.status_code == 401

    def test_wrong_key_is_403(self, keyed_client):
        resp = keyed_client.post(
            "/v1/options/price", json=PRICE_BODY, headers={"X-API-Key": "not-a-real-key"}
        )
        assert resp.status_code == 403

    def test_valid_key_prices(self, keyed_client):
        resp = keyed_client.post(
            "/v1/options/price", json=PRICE_BODY, headers={"X-API-Key": PRICE_KEY}
        )
        assert resp.status_code == 200

    def test_health_needs_no_key(self, keyed_client):
        assert keyed_client.get("/health/live").status_code == 200


class TestScopes:
    def test_price_key_cannot_fit(self, keyed_client):
        resp = keyed_client.post(
            "/v1/vol-surface/fit",
            headers={"X-API-Key": PRICE_KEY},
            json={
                "forward": 100,
                "quotes": [{"strike": 100, "expiry": 1, "implied_vol": 0.2}] * 10,
            },
        )
        assert resp.status_code == 403
        assert "scope" in resp.json()["detail"]

    def test_fit_key_cannot_price(self, keyed_client):
        resp = keyed_client.post(
            "/v1/options/price", json=PRICE_BODY, headers={"X-API-Key": FIT_KEY}
        )
        assert resp.status_code == 403

    def test_price_key_cannot_read_audit(self, keyed_client):
        resp = keyed_client.get("/v1/admin/audit", headers={"X-API-Key": PRICE_KEY})
        assert resp.status_code == 403

    def test_admin_key_reads_audit(self, keyed_client):
        resp = keyed_client.get("/v1/admin/audit", headers={"X-API-Key": ADMIN_KEY})
        assert resp.status_code == 200

    def test_all_scopes_key_does_everything(self, keyed_client):
        headers = {"X-API-Key": ALL_KEY}
        assert (
            keyed_client.post("/v1/options/price", json=PRICE_BODY, headers=headers).status_code
            == 200
        )
        assert keyed_client.get("/v1/admin/audit", headers=headers).status_code == 200


class TestRateLimit:
    def test_limit_returns_429_with_retry_after(self, make_client):
        with make_client(QF_RATE_LIMIT="3/minute") as limited:
            for _ in range(3):
                assert limited.post("/v1/options/price", json=PRICE_BODY).status_code == 200
            resp = limited.post("/v1/options/price", json=PRICE_BODY)
            assert resp.status_code == 429
            assert int(resp.headers["Retry-After"]) >= 1
