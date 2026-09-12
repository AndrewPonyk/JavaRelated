"""Meta endpoints — OpenAPI spec + a lightweight HTML API explorer."""

from __future__ import annotations

from flask import Blueprint, jsonify

bp = Blueprint("meta", __name__)

_ERR = {
    "type": "object",
    "properties": {
        "error": {
            "type": "object",
            "properties": {
                "code": {"type": "string"},
                "message": {"type": "string"},
                "correlation_id": {"type": "string"},
            },
        }
    },
}


def _op(summary: str, body: dict | None = None, responses: dict | None = None) -> dict:
    op: dict = {"summary": summary, "responses": responses or {"200": {"description": "OK"}}}
    if body:
        op["requestBody"] = {
            "required": True,
            "content": {"application/json": {"schema": {"type": "object", "example": body}}},
        }
    op["responses"]["default"] = {
        "description": "Error envelope",
        "content": {"application/json": {"schema": _ERR}},
    }
    return op


def build_spec() -> dict:
    """Hand-maintained OpenAPI 3.0 document — matches the live routes."""
    return {
        "openapi": "3.0.3",
        "info": {
            "title": "Cryptography Toolkit API",
            "version": "1.0.0",
            "description": (
                "Educational crypto demos: AES (GCM/CBC), RSA (OAEP/PSS), ECDSA "
                "(incl. k-reuse recovery), SHA-3, Argon2, TLS 1.3 walkthrough, and "
                "an attack gallery with countermeasures. All responses use the "
                "envelope {data} / {error:{code,message}}."
            ),
        },
        "servers": [{"url": "/api"}],
        "paths": {
            "/health": {"get": _op("Liveness probe")},
            "/openapi.json": {"get": _op("This document")},
            # --- AES ---
            "/aes/encrypt": {
                "post": _op(
                    "AES encrypt (GCM default; CBC/ECB demo-only)",
                    {"plaintext": "attack at dawn", "key_b64": "…", "mode": "gcm"},
                )
            },
            "/aes/decrypt": {
                "post": _op(
                    "AES decrypt with GCM tag verification",
                    {"ciphertext_b64": "…", "iv_b64": "…", "tag_b64": "…", "key_b64": "…"},
                )
            },
            # --- SHA-3 / Argon2 ---
            "/sha3/digest": {
                "post": _op(
                    "SHA3-256/384/512 digest",
                    {"message": "abc", "algorithm": "sha3-256"},
                )
            },
            "/argon2/hash": {
                "post": _op(
                    "Argon2id hash with pinned presets",
                    {"password": "correct horse", "preset": "interactive"},
                )
            },
            "/argon2/verify": {
                "post": _op(
                    "Verify a password against an Argon2 hash",
                    {"hash_string": "$argon2id$…", "password": "…"},
                )
            },
            # --- RSA ---
            "/rsa/keygen": {"post": _op("Generate >=2048-bit RSA keypair", {"bits": 2048})},
            "/rsa/encrypt": {
                "post": _op(
                    "RSA-OAEP(SHA-256) encrypt",
                    {"public_pem": "-----BEGIN…", "plaintext": "short message"},
                )
            },
            "/rsa/decrypt": {
                "post": _op(
                    "RSA-OAEP decrypt",
                    {"private_pem": "-----BEGIN…", "ciphertext_b64": "…"},
                )
            },
            "/rsa/sign": {
                "post": _op(
                    "RSA-PSS(SHA-256) sign",
                    {"private_pem": "-----BEGIN…", "message": "doc"},
                )
            },
            "/rsa/verify": {
                "post": _op(
                    "RSA-PSS verify",
                    {"public_pem": "-----BEGIN…", "message": "doc", "signature_b64": "…"},
                )
            },
            "/rsa/attack/malleability": {
                "post": _op(
                    "Textbook-RSA malleability (gallery)",
                    {"public_pem": "…", "private_pem": "…", "message": "42", "demo": True},
                )
            },
            # --- ECDSA ---
            "/ecdsa/keygen": {"post": _op("Generate P-256/P-384 keypair", {"curve": "P-256"})},
            "/ecdsa/sign": {
                "post": _op(
                    "ECDSA sign (random-k or RFC 6979)",
                    {"private_pem": "…", "message": "doc", "mode": "deterministic-rfc6979"},
                )
            },
            "/ecdsa/verify": {
                "post": _op(
                    "ECDSA verify",
                    {"public_pem": "…", "message": "doc", "signature_b64": "…"},
                )
            },
            "/ecdsa/determinism": {
                "post": _op(
                    "Random vs deterministic nonce behaviour",
                    {"private_pem": "…", "message": "doc"},
                )
            },
            "/ecdsa/attack/k-reuse": {
                "post": _op(
                    "Sign twice with one k, recover the private key (gallery)",
                    {"private_pem": "…", "message1": "first", "message2": "second", "demo": True},
                )
            },
            # --- TLS ---
            "/tls13/handshake": {"get": _op("Full TLS 1.3 key-schedule walkthrough (?suite=…)")},
            "/tls13/hkdf": {
                "post": _op(
                    "Single HKDF-Expand-Label step",
                    {"ikm_hex": "…", "info_label": "c hs traffic", "length": 32},
                )
            },
            "/tls13/downgrade": {"get": _op("Downgrade-stripping simulation + defences")},
            # --- attacks ---
            "/attacks/ecb-penguin": {"post": _op("ECB pattern leakage visual")},
            "/attacks/length-extension": {
                "post": _op(
                    "SHA-256(key||msg) forgery vs HMAC",
                    {"message": "amount=100&to=alice", "append": "&admin=true"},
                )
            },
            "/attacks/gcm-nonce-reuse": {
                "post": _op(
                    "Keystream reuse XOR recovery",
                    {"known_plaintext": "…", "secret_plaintext": "…"},
                )
            },
            # --- lessons ---
            "/lessons/": {
                "get": _op("List lessons (?topic=…&limit=&offset=)"),
                "post": _op(
                    "Create lesson (admin)",
                    {"slug": "new-lesson", "title": "Title", "topic": "aes"},
                ),
            },
            "/lessons/{slug}": {
                "get": _op("Fetch one lesson"),
                "patch": _op("Update lesson (admin)", {"title": "New title"}),
                "delete": _op("Delete lesson (admin)"),
            },
            # --- auth ---
            "/auth/register": {
                "post": _op(
                    "Register (first user becomes admin)",
                    {"email": "a@b.c", "password": "min8chars"},
                )
            },
            "/auth/login": {
                "post": _op(
                    "Login (+TOTP when enabled)",
                    {"email": "a@b.c", "password": "…", "totp_code": "123456"},
                )
            },
            "/auth/whoami": {"get": _op("Current user from bearer token")},
            "/auth/totp/setup": {"post": _op("Generate TOTP secret + otpauth URI")},
            "/auth/totp/enable": {
                "post": _op(
                    "Confirm TOTP enrolment",
                    {"secret_base32": "…", "code": "123456"},
                )
            },
            "/auth/totp/disable": {
                "post": _op(
                    "Disable TOTP (password + code)",
                    {"code": "123456", "password": "…"},
                )
            },
            "/auth/audit": {"get": _op("Audit log listing (admin, ?limit=&offset=)")},
        },
    }


@bp.get("/openapi.json")
def openapi_json():
    return jsonify(build_spec())


@bp.get("/docs")
def docs():
    rows = "".join(
        f"<tr><td><code>{method.upper()}</code></td><td><code>{path}</code></td><td>{op['summary']}</td></tr>"
        for path, methods in build_spec()["paths"].items()
        for method, op in methods.items()
    )
    html = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cryptography Toolkit — API</title>
<style>
body{{font-family:system-ui,sans-serif;margin:2rem auto;max-width:900px;padding:0 1rem;color:#1c1e21}}
code{{background:#f0f0f2;padding:.1rem .3rem;border-radius:4px;font-size:.9em}}
table{{border-collapse:collapse;width:100%}}
td,th{{border-bottom:1px solid #e5e7eb;padding:.5rem .6rem;text-align:left}}
td:first-child{{width:5rem;font-weight:700}}td:nth-child(2){{width:18rem}}
a{{color:#2563eb}}</style></head><body>
<h1>Cryptography Toolkit — API</h1>
<p>Machine-readable spec: <a href="/api/openapi.json"><code>/api/openapi.json</code></a></p>
<table><thead><tr><th>Method</th><th>Path</th><th>Summary</th></tr></thead><tbody>{rows}</tbody></table>
<p>Every response shares the envelope <code>{{"data": …}}</code> or
<code>{{"error":{{"code","message"}}}}</code>. Attack endpoints are rate-limited.</p>
</body></html>"""
    return html
