"""TLS 1.3 handshake demonstration — a REAL (simplified) key schedule.

The handshake below performs genuine cryptography: X25519 key exchange,
the full RFC 8446 §7.1 HKDF key schedule, transcript hashing, Finished
MACs, and AES-GCM application records. Only the wire encodings are
simplified (readable tags instead of TLS record framing) so each step can
be shown in the UI.

IMPORTANT (docs/TECH-NOTES.md §3.6 #7): this is a *rendered simulation*,
labelled as such in the UI. The *real* TLS 1.3 endpoint is nginx itself —
students verify with browser devtools / openssl s_client against it.
"""

from __future__ import annotations

import hashlib
import hmac
import os

from cryptography.hazmat.primitives.asymmetric.x25519 import (
    X25519PrivateKey,
    X25519PublicKey,
)
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

from crypto_toolkit.services import kdf

CIPHER_SUITES = {
    # suite -> (hash, key_bytes)  — AES-GCM both, differing hash/key sizes
    "TLS_AES_128_GCM_SHA256": (hashlib.sha256, 16),
    "TLS_AES_256_GCM_SHA384": (hashlib.sha384, 32),
}
DEFAULT_SUITE = "TLS_AES_128_GCM_SHA256"
DOWNGRADE_SENTINEL = bytes.fromhex("444F574E47524401")  # "DOWNGRD\x01" per RFC 8446 §4.1.3


# ---- wire encodings (simplified but structurally faithful) ----


def _client_hello_bytes(client_random: bytes, key_share: bytes, suites: list) -> bytes:
    return b"ClientHello" + client_random + key_share + b"|".join(s.encode() for s in suites)


def _server_hello_bytes(server_random: bytes, key_share: bytes, suite: str) -> bytes:
    return b"ServerHello" + server_random + key_share + suite.encode()


def _msg(tag: str, payload: bytes = b"") -> bytes:
    return tag.encode() + payload


def _xor(a: bytes, b: bytes) -> bytes:
    return bytes(x ^ y for x, y in zip(a, b, strict=False))


def handshake(suite: str = DEFAULT_SUITE) -> dict:
    """Run the full 1-RTT handshake and return every intermediate value."""
    if suite not in CIPHER_SUITES:
        raise ValueError(f"Unsupported suite {suite!r}; use one of {sorted(CIPHER_SUITES)}.")
    hashmod, key_len = CIPHER_SUITES[suite]
    hash_len = hashmod().digest_size

    # --- 1. ClientHello: fresh X25519 keyshare ---
    client_priv = X25519PrivateKey.generate()
    client_pub = client_priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    client_random = os.urandom(32)

    # --- 2. ServerHello: server keyshare, suite selection ---
    server_priv = X25519PrivateKey.generate()
    server_pub = server_priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    server_random = os.urandom(32)

    ch = _client_hello_bytes(client_random, client_pub, sorted(CIPHER_SUITES))
    sh = _server_hello_bytes(server_random, server_pub, suite)

    # --- 3. X25519 exchange + RFC 8446 §7.1 key schedule ---
    shared = client_priv.exchange(X25519PublicKey.from_public_bytes(server_pub))
    zero = b"\x00" * hash_len
    early_secret = kdf.hkdf_extract(salt=zero, ikm=zero, hashmod=hashmod)
    hs_salt = kdf.derive_secret(early_secret, "derived", b"", hashmod)
    handshake_secret = kdf.hkdf_extract(salt=hs_salt, ikm=shared, hashmod=hashmod)

    th_ch_sh = hashmod(ch + sh).digest()
    c_hs = kdf.derive_secret(handshake_secret, "c hs traffic", ch + sh, hashmod)
    s_hs = kdf.derive_secret(handshake_secret, "s hs traffic", ch + sh, hashmod)

    # --- 4. Server flight (encrypted under handshake keys in real TLS) ---
    ee = _msg("EncryptedExtensions", b"alpn=h2;no_early_data")
    cert = _msg("Certificate", b"CN=cryptotoolkit.demo")
    cert_verify = _msg("CertificateVerify", b"rsa-pss over transcript so far")
    fin_key_s = kdf.hkdf_expand_label(s_hs, "finished", b"", hash_len, hashmod)
    transcript_s = ch + sh + ee + cert + cert_verify
    server_fin = _msg("Finished", hmac.new(fin_key_s, hashmod(transcript_s).digest(), hashmod).digest())

    # --- 5. Client Finished + application secrets ---
    fin_key_c = kdf.hkdf_expand_label(c_hs, "finished", b"", hash_len, hashmod)
    transcript_c = transcript_s + server_fin
    client_fin = _msg("Finished", hmac.new(fin_key_c, hashmod(transcript_c).digest(), hashmod).digest())

    master_salt = kdf.derive_secret(handshake_secret, "derived", b"", hashmod)
    master_secret = kdf.hkdf_extract(salt=master_salt, ikm=zero, hashmod=hashmod)
    full_transcript = transcript_c + client_fin
    c_ap = kdf.derive_secret(master_secret, "c ap traffic", transcript_c, hashmod)
    s_ap = kdf.derive_secret(master_secret, "s ap traffic", transcript_c, hashmod)
    exp_master = kdf.derive_secret(master_secret, "exp master", transcript_c, hashmod)
    res_master = kdf.derive_secret(master_secret, "res master", full_transcript, hashmod)

    # --- 6. Application data: real AES-GCM record with per-record nonce ---
    static_iv = kdf.hkdf_expand_label(c_ap, "iv", b"", 12, hashmod)
    record_key = kdf.hkdf_expand_label(c_ap, "key", b"", key_len, hashmod)
    seq = 0
    record_nonce = _xor(static_iv, seq.to_bytes(12, "big"))
    aad = b"record:app:seq0"
    plaintext = b"Hello over TLS 1.3!"
    ct = AESGCM(record_key).encrypt(record_nonce, plaintext, aad)

    # --- 7. Resumption ticket ---
    ticket_nonce = os.urandom(8)
    ticket = kdf.hkdf_expand_label(res_master, "resumption", ticket_nonce, hash_len, hashmod)

    return {
        "note": "SIMULATION with real crypto — inspect the live connection for actual TLS 1.3.",
        "suite": suite,
        "steps": [
            {
                "idx": 0,
                "name": "client_hello",
                "detail": (
                    "Client sends supported versions + X25519 key share. "
                    "In TLS 1.3 the key exchange starts on the first flight."
                ),
                "client_random": client_random.hex(),
                "key_share": client_pub.hex(),
                "suites": sorted(CIPHER_SUITES),
            },
            {
                "idx": 1,
                "name": "server_hello",
                "detail": "Server picks the suite and returns its key share — 1-RTT handshake by design.",
                "server_random": server_random.hex(),
                "key_share": server_pub.hex(),
                "downgrade_sentinel_bytes": DOWNGRADE_SENTINEL.hex(),
            },
            {
                "idx": 2,
                "name": "derive_handshake_keys",
                "detail": (
                    "X25519 shared secret feeds HKDF-Extract; traffic secrets are "
                    "domain-separated by labels and the transcript hash."
                ),
                "shared_secret": shared.hex(),
                "handshake_secret": handshake_secret.hex(),
                "transcript_hash_ch_sh": th_ch_sh.hex(),
                "client_hs_traffic": c_hs.hex(),
                "server_hs_traffic": s_hs.hex(),
            },
            {
                "idx": 3,
                "name": "server_certificates",
                "detail": (
                    "All remaining handshake messages fly encrypted under the handshake "
                    "keys — certificate data is invisible to passive observers."
                ),
                "encrypted_extensions": ee.hex(),
                "certificate": cert.hex(),
                "certificate_verify": cert_verify.hex(),
            },
            {
                "idx": 4,
                "name": "server_finished",
                "detail": "Server MACs the whole transcript with a key derived from its traffic secret.",
                "finished_key": fin_key_s.hex(),
                "verify_data": server_fin.hex(),
            },
            {
                "idx": 5,
                "name": "client_finished",
                "detail": "Client MACs the transcript; both sides then derive application traffic secrets.",
                "finished_key": fin_key_c.hex(),
                "verify_data": client_fin.hex(),
                "master_secret": master_secret.hex(),
                "client_ap_traffic": c_ap.hex(),
                "server_ap_traffic": s_ap.hex(),
                "exporter_master": exp_master.hex(),
            },
            {
                "idx": 6,
                "name": "application_data",
                "detail": (
                    "Each record: nonce = static_iv XOR seq, AES-GCM with AAD binding sequence and type."
                ),
                "record_key": record_key.hex(),
                "static_iv": static_iv.hex(),
                "record_nonce": record_nonce.hex(),
                "aad": aad.hex(),
                "ciphertext": ct.hex(),
                "plaintext_preview": plaintext.decode(),
            },
            {
                "idx": 7,
                "name": "resumption",
                "detail": (
                    "PSK resumption derives from resumption_master; 0-RTT early "
                    "data trades replay protection for latency."
                ),
                "resumption_master": res_master.hex(),
                "ticket": ticket.hex(),
                "zero_rtt_warning": "0-RTT data is replayable — servers must treat it as idempotent-only.",
            },
        ],
    }


def downgrade_simulation() -> dict:
    """How a 'version downgrade / stripping' MITM attack works — and fails.

    Classic attack (TLS ≤1.2): an on-path attacker deletes the TLS 1.3
    extensions (supported_versions, key_share, signature_algorithms...) so a
    client that must interoperate falls back to old TLS. Defences, in the
    order they bite:

    1. RFC 8446 §4.1.3 — a TLS-1.3-capable server forced to negotiate 1.2
       writes the sentinel bytes 'DOWNGRD\\x01' into ServerHello.random;
       the client inspects them and ABORTS the handshake.
    2. Encrypted handshake — after ServerHello, negotiation happens under
       encryption; the attacker can no longer read what to strip.
    3. HSTS + ssl_protocols TLSv1.3-only (our edge config) — the client
       refuses plaintext fallback entirely: nothing to strip.
    """
    random_hex = os.urandom(24).hex() + DOWNGRADE_SENTINEL.hex()
    return {
        "warning": (
            "Attack illustration — shows why downgrade stripping no longer works against TLS 1.3 clients."
        ),
        "attack_steps": [
            "Attacker sits on-path (rogue AP, hostile proxy) and removes "
            "supported_versions + key_share from ClientHello.",
            "A TLS 1.2-era client, wanting interop, retries with a legacy ClientHello.",
            "Server (which supports 1.3!) negotiates TLS 1.2 and marks "
            "ServerHello.random with the sentinel.",
            "TLS 1.3 client detects 'DOWNGRD\\x01' in the last 8 bytes → treats it as "
            "an attack → aborts with illegal_parameter.",
        ],
        "defenses": [
            {
                "name": "downgrade sentinel",
                "detail": "Server signals 'I was forced below my best version' inside ServerHello.random.",
                "server_random_with_sentinel": random_hex,
            },
            {
                "name": "encrypted negotiation",
                "detail": (
                    "From ServerHello onward, extensions and certificates are "
                    "encrypted — blind stripping is useless."
                ),
            },
            {
                "name": "HSTS + TLS-1.3-only edge",
                "detail": (
                    "nginx/tls/edge.conf sets ssl_protocols TLSv1.3 and HSTS; "
                    "the browser refuses any fallback."
                ),
                "config": "ssl_protocols TLSv1.3; add_header Strict-Transport-Security ...",
            },
        ],
        "countermeasure": (
            "Keep ssl_protocols pinned to TLSv1.3, enable HSTS, and never enable "
            "renegotiation-based fallback."
        ),
    }
