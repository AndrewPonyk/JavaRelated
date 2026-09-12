"""ECDSA service — P-256 signing/verifying plus the k-reuse attack demo.

Secure paths use PyCryptodome's DSS ('fips-186-3' random-k and
'deterministic-rfc6979'). The attack demo signs with a FIXED, REUSED nonce
using explicit curve arithmetic — the exact mistake that leaked Sony's PS3
key in 2010 — then recovers the private key with two lines of arithmetic.
"""

from __future__ import annotations

import base64

from Crypto.Hash import SHA256
from Crypto.PublicKey import ECC
from Crypto.PublicKey.ECC import _curves
from Crypto.Signature import DSS

from crypto_toolkit.errors import (
    InvalidInputError,
    VerificationError,
)

SUPPORTED_CURVES = frozenset({"P-256", "P-384"})
_CURVE_TABLE = {"P-256": "NIST P-256", "P-384": "NIST P-384"}
_SIG_MODES = frozenset({"fips-186-3", "deterministic-rfc6979"})


def generate_keypair(curve: str = "P-256") -> dict:
    if curve not in SUPPORTED_CURVES:
        raise InvalidInputError(f"Curve {curve!r} not supported; use one of {sorted(SUPPORTED_CURVES)}.")
    key = ECC.generate(curve=curve)
    return {
        "curve": curve,
        "public_pem": key.public_key().export_key(format="PEM"),
        "private_pem": key.export_key(format="PEM"),
        "note": "Demo keypair — in production the private key never leaves the client/HSM.",
    }


def _import(pem: str, what: str) -> ECC.EccKey:
    if not isinstance(pem, str) or "BEGIN" not in pem:
        raise InvalidInputError(f"{what} is not a PEM string.")
    try:
        return ECC.import_key(pem)
    except ValueError as exc:
        raise InvalidInputError(f"{what} is not a valid ECC key: {exc}") from exc


def _msg_hash(message: bytes):
    return SHA256.new(message)


def sign(private_pem: str, message: bytes, mode: str = "fips-186-3") -> dict:
    if mode not in _SIG_MODES:
        raise InvalidInputError(f"Mode {mode!r} not supported; use one of {sorted(_SIG_MODES)}.")
    key = _import(private_pem, "private_pem")
    if not key.has_private():
        raise InvalidInputError("Signing requires the private key.")
    signer = DSS.new(key, mode, encoding="der")
    signature = signer.sign(_msg_hash(message))
    return {
        "mode": mode,
        "signature": base64.b64encode(signature).decode(),
        "encoding": "DER",
    }


def verify(public_pem: str, message: bytes, signature_b64: str) -> dict:
    key = _import(public_pem, "public_pem")
    try:
        signature = base64.b64decode(signature_b64, validate=True)
    except ValueError as exc:
        raise InvalidInputError(f"signature is not valid base64: {exc}") from exc
    try:
        DSS.new(key, "fips-186-3", encoding="der").verify(_msg_hash(message), signature)
    except ValueError as exc:
        raise VerificationError(f"ECDSA signature verification failed: {exc}") from exc
    return {"verified": True, "scheme": "ecdsa-p256-sha256"}


def determinism_demo(private_pem: str, message: bytes) -> dict:
    """Sign the same message twice under each mode.

    fips-186-3 draws a fresh random k -> different signatures.
    deterministic-rfc6979 derives k from the key+message -> identical.
    RFC 6979 exists precisely to make k-reuse (see below) impossible.
    """
    random_a = sign(private_pem, message, mode="fips-186-3")
    random_b = sign(private_pem, message, mode="fips-186-3")
    det_a = sign(private_pem, message, mode="deterministic-rfc6979")
    det_b = sign(private_pem, message, mode="deterministic-rfc6979")
    return {
        "random_signatures_differ": random_a["signature"] != random_b["signature"],
        "deterministic_signatures_identical": det_a["signature"] == det_b["signature"],
        "note": (
            "Random-k is safe ONLY with a perfect CSPRNG. RFC 6979 removes the "
            "failure mode entirely by deriving k from (d, h)."
        ),
    }


# ---------------- attack gallery: k-reuse key recovery ----------------


def _curve_params(curve: str):
    """Generator point and group order as integers for explicit-k signing."""
    entry = _curves[_CURVE_TABLE[curve]]
    return entry.G, int(entry.order)


def sign_with_explicit_k(private_pem: str, message: bytes, k: int, curve: str = "P-256") -> dict:
    """ATTACK DEMO ONLY — sign with a caller-chosen k (normally never done).

    Uses raw curve arithmetic so we control the nonce, exactly reproducing
    what happens inside ECDSA without the library hiding it.
    """
    key = _import(private_pem, "private_pem")
    if not key.has_private():
        raise InvalidInputError("Signing requires the private key.")
    G, n = _curve_params(curve)
    d = int(key.d)
    if not 1 <= k < n:
        raise InvalidInputError("k must satisfy 1 <= k < n.")
    # FIPS 186-3: e = leftmost min(bitlen(n), bitlen(hash)) bits — NOT reduced mod n.
    h = int.from_bytes(_msg_hash(message).digest(), "big")

    r = int((G * k).x) % n
    if r == 0:
        raise InvalidInputError("Unlucky k produced r=0; choose another.")
    s = (pow(k, -1, n) * (h + r * d)) % n
    if s == 0:
        raise InvalidInputError("Unlucky k produced s=0; choose another.")

    # DER-encode so verify() (the standard library path) accepts it.
    def _der_int(x: int) -> bytes:
        b = x.to_bytes((x.bit_length() + 8) // 8, "big")
        return b"\x02" + bytes([len(b)]) + b

    der = b"\x30" + bytes([len(_der_int(r)) + len(_der_int(s))]) + _der_int(r) + _der_int(s)
    return {"r": r, "s": s, "k_used": k, "signature": base64.b64encode(der).decode()}


def recover_key_from_reused_k(
    message1: bytes, sig1: dict, message2: bytes, sig2: dict, curve: str = "P-256"
) -> dict:
    """ATTACK DEMO ONLY — recover the private key from two signatures sharing k.

    With the same k:  k = (h1 - h2) / (s1 - s2)  mod n
    then:             d = (s1*k - h1) / r        mod n
    This is the Sony PS3 leak (2010) and the Android Bitcoin wallet bug (2013).
    """
    _, n = _curve_params(curve)

    def _h(msg: bytes) -> int:
        return int.from_bytes(_msg_hash(msg).digest(), "big")

    h1, h2 = _h(message1), _h(message2)
    s1, s2, r1, r2 = int(sig1["s"]), int(sig2["s"]), int(sig1["r"]), int(sig2["r"])
    if r1 != r2:
        raise InvalidInputError("Signatures do not share a nonce (r values differ).")
    denom = (s1 - s2) % n
    if denom == 0:
        raise InvalidInputError("s1 == s2; cannot recover (identical messages?).")
    k = ((h1 - h2) * pow(denom, -1, n)) % n
    r_inv = pow(r1, -1, n)
    d = ((s1 * k - h1) * r_inv) % n
    return {
        "warning": "Attack gallery — key recovery from ECDSA nonce reuse.",
        "recovered_k": k,
        "recovered_private_key_d": d,
        "explanation": (
            "Both signatures used the same ephemeral nonce k. Two equations, two unknowns "
            "(k, d): the attacker solves for k first, then for the private key d — using "
            "nothing but the two public signatures and the messages."
        ),
        "countermeasure": "RFC 6979 deterministic nonces (or a vetted CSPRNG) make reuse impossible.",
    }
