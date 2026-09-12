"""API surface tests — every endpoint responds with the documented envelope,
error shapes are uniform, and the OpenAPI spec lists all live routes."""

import base64
import os


class TestEnvelope:
    def test_health(self, client):
        res = client.get("/api/health")
        assert res.status_code == 200
        assert res.get_json() == {"data": {"status": "ok", "service": "crypto-toolkit"}}

    def test_non_json_body_422_envelope(self, client):
        res = client.post("/api/aes/encrypt", data="not json", content_type="text/plain")
        assert res.status_code == 422
        assert set(res.get_json()["error"]) >= {"code", "message"}

    def test_bad_mode_schema_envelope(self, client):
        res = client.post(
            "/api/aes/encrypt",
            json={
                "plaintext": "x",
                "key_b64": "aGVsbG8=",
                "mode": "aes-ecb-of-doom",
            },
        )
        assert res.status_code == 422
        assert res.get_json()["error"]["code"] == "invalid_input"

    def test_unknown_route_404_envelope(self, client):
        res = client.get("/api/definitely-not-a-route")
        assert res.status_code == 404
        assert "error" in res.get_json()


class TestCryptoRoutes:
    def test_aes_round_trip(self, client):
        key = base64.b64encode(os.urandom(32)).decode()
        enc = client.post(
            "/api/aes/encrypt",
            json={
                "plaintext": "classical example",
                "key_b64": key,
                "mode": "gcm",
            },
        ).get_json()["data"]
        dec = client.post(
            "/api/aes/decrypt",
            json={
                "ciphertext_b64": enc["ciphertext"],
                "iv_b64": enc["iv"],
                "tag_b64": enc["tag"],
                "key_b64": key,
                "mode": "gcm",
            },
        )
        assert dec.get_json()["data"]["plaintext"] == "classical example"

    def test_cbc_round_trip(self, client):
        key = base64.b64encode(os.urandom(32)).decode()
        enc = client.post(
            "/api/aes/encrypt",
            json={
                "plaintext": "sixteen bytes ok!",
                "key_b64": key,
                "mode": "cbc",
                "demo": True,
            },
        ).get_json()["data"]
        dec = client.post(
            "/api/aes/decrypt",
            json={
                "ciphertext_b64": enc["ciphertext"],
                "iv_b64": enc["iv"],
                "key_b64": key,
                "mode": "cbc",
            },
        )
        assert dec.status_code == 200
        assert dec.get_json()["data"]["plaintext"] == "sixteen bytes ok!"

    def test_sha3_route(self, client):
        res = client.post("/api/sha3/digest", json={"message": "abc"})
        assert res.get_json()["data"]["digest_hex"].startswith("3a985da7")

    def test_argon2_round_trip(self, client):
        h = client.post("/api/argon2/hash", json={"password": "pw-secret", "preset": "interactive"})
        assert h.status_code == 200
        ok = client.post(
            "/api/argon2/verify",
            json={
                "hash_string": h.get_json()["data"]["hash"],
                "password": "pw-secret",
            },
        )
        assert ok.get_json()["data"]["verified"] is True

    def test_tls_routes(self, client):
        assert client.get("/api/tls13/handshake").status_code == 200
        assert client.get("/api/tls13/downgrade").status_code == 200
        res = client.post("/api/tls13/hkdf", json={"ikm_hex": "ab" * 32, "info_label": "key", "length": 16})
        assert len(res.get_json()["data"]["output_key_material_hex"]) == 32

    def test_hkdf_bad_hex_422(self, client):
        assert client.post("/api/tls13/hkdf", json={"ikm_hex": "zz"}).status_code == 422

    def test_attack_routes(self, client):
        for path, payload in [
            ("/api/attacks/ecb-penguin", {}),
            ("/api/attacks/length-extension", {}),
            ("/api/attacks/gcm-nonce-reuse", {}),
        ]:
            res = client.post(path, json=payload)
            assert res.status_code == 200, path


class TestOpenApi:
    def test_spec_lists_live_routes(self, client):
        # /api/openapi.json serves the RAW spec (standard tooling contract),
        # outside the data/error envelope used elsewhere.
        spec = client.get("/api/openapi.json").get_json()
        assert spec["openapi"].startswith("3.")
        listed = {f"{method.upper()} {path}" for path, ops in spec["paths"].items() for method in ops}
        for route in (
            "/health",
            "/aes/encrypt",
            "/rsa/keygen",
            "/ecdsa/sign",
            "/tls13/handshake",
            "/attacks/ecb-penguin",
            "/auth/login",
            "/lessons/",
            "/lessons/{slug}",
        ):
            assert any(r.endswith(route) or route in r for r in listed), route

    def test_docs_html_renders(self, client):
        res = client.get("/api/docs")
        assert res.status_code == 200 and "text/html" in res.content_type
        assert "/api/openapi.json" in res.get_data(as_text=True)


class TestConfig:
    def test_unknown_env_rejected(self):
        import pytest

        from crypto_toolkit.config import get_config

        with pytest.raises(ValueError):
            get_config("chaos")

    def test_prod_requires_secret(self, monkeypatch):
        import pytest

        from crypto_toolkit.config import get_config

        monkeypatch.setenv("FLASK_ENV", "production")
        monkeypatch.delenv("SECRET_KEY", raising=False)
        with pytest.raises(RuntimeError, match="SECRET_KEY"):
            get_config()
