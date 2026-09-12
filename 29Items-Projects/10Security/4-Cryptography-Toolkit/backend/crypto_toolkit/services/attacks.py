"""Attack gallery — every classic break, paired with its countermeasure.

Each demo returns a structured result including the `countermeasure` that
defeats it. Insecure primitives are used deliberately, clearly labelled,
and only ever inside this module.
"""

from __future__ import annotations

import hashlib
import hmac
import os

from Crypto.Cipher import AES

from crypto_toolkit.errors import InvalidInputError
from crypto_toolkit.services import sha256_midstate

# ---------------------------------------------------------------- ECB penguin
# Rows are exactly 16 chars: one AES block per row, so identical rows are
# identical blocks and the shape survives ECB verbatim.

_PENGUIN = [
    "  ............  ",
    " .############. ",
    ".##############.",
    "......####......",
    ".....######.....",
    "....########....",
    "....########....",
    "....########....",
    "....########....",
    ".....######.....",
    "......####......",
    "................",
]


def ecb_penguin() -> dict:
    """The ECB 'penguin': identical plaintext blocks -> identical ciphertext
    blocks, so structure leaks straight through encryption."""
    key = os.urandom(32)
    rows = [row.encode() for row in _PENGUIN]
    if any(len(row) != 16 for row in rows):  # one block per row by construction
        raise InvalidInputError("Penguin rows must each be exactly one 16-byte block.")

    def fingerprint(blocks: list) -> list:
        """One symbol per 16-byte block; identical blocks -> identical symbol."""
        seen: dict = {}
        symbols = "abcdefghijklmnopqrstuvwxyz0123456789"
        out = []
        for blk in blocks:
            if blk not in seen:
                seen[blk] = symbols[len(seen) % len(symbols)]
            out.append(seen[blk])
        return out

    ecb = AES.new(key, AES.MODE_ECB)
    ct_ecb = [ecb.encrypt(row) for row in rows]
    cbc = AES.new(key, AES.MODE_CBC, iv=os.urandom(16))
    ct_cbc = [cbc.encrypt(row) for row in rows]

    def to_rows(ciphertext_rows: list) -> list:
        return [symbol * 16 for symbol in fingerprint(ciphertext_rows)]

    return {
        "warning": "ECB mode — attack gallery only.",
        "plaintext": _PENGUIN,
        "ecb_blocks": to_rows(ct_ecb),
        "cbc_blocks": to_rows(ct_cbc),
        "observation": (
            "The penguin survives ECB: repeated input blocks produce repeated output "
            "blocks, so the image's structure is visible. CBC with a random IV "
            "destroys the pattern."
        ),
        "countermeasure": "Never ECB. Use GCM (authenticated) or CBC+HMAC with fresh IVs.",
    }


# -------------------------------------------------- SHA-256 length extension


def length_extension(message: str = "amount=100&to=alice", append: str = "&admin=true") -> dict:
    """Forge a valid SHA256(key||msg||append) MAC without knowing the key.

    Merkle–Damgård hashes are extensions of their state: given H(key||msg)
    and len(key||msg), anyone can compute H(key||msg||pad||append). The
    demo runs a tiny 'server' that MACs with SHA256(key||msg) and accepts
    the forged tag — then shows HMAC and SHA-3 refusing it.
    """
    secret_key = os.urandom(16)  # the attacker never sees this
    original = message.encode()
    suffix = append.encode()
    if len(suffix) > 128:
        raise InvalidInputError("Append is limited to 128 bytes in the demo.")

    # --- "server" side ---
    mac_original = hashlib.sha256(secret_key + original).digest()

    # --- attacker side (no key!): midstate + padding + suffix ---
    glue = sha256_midstate.md_padding(len(secret_key) + len(original))
    forged_msg = original + glue + suffix
    forged_mac = sha256_midstate.digest_from_state(
        state=mac_original, data=suffix, prefix_len=len(secret_key) + len(original) + len(glue)
    )

    # --- verification: naive construction accepts, HMAC/SHA-3 reject ---
    naive_accepts = hmac.compare_digest(hashlib.sha256(secret_key + forged_msg).digest(), forged_mac)
    # attacker's forged message under HMAC (they cannot compute it without the key)
    hmac_forged_guess = forged_mac  # what the attacker would present as the "MAC"
    hmac_accepts = hmac.compare_digest(
        hmac.new(secret_key, forged_msg, hashlib.sha256).digest(), hmac_forged_guess
    )

    return {
        "warning": "Attack gallery — length extension on Merkle–Damgård hashes.",
        "secret_key_len": len(secret_key),
        "original_message": message,
        "original_mac_hex": mac_original.hex(),
        "glue_padding_hex": glue.hex(),
        "forged_message_utf8_lossy": forged_msg.decode("latin1"),
        "forged_mac_hex": forged_mac.hex(),
        "server_accepts_naive_sha256_key_msg": naive_accepts,
        "server_accepts_hmac": hmac_accepts,
        "observation": (
            "The forged MAC validates against SHA256(key||msg||pad||append) even "
            "though the attacker never learned the key — the hash state was simply "
            "resumed. HMAC (and SHA-3's sponge) are immune to this."
        ),
        "countermeasure": "HMAC-SHA256 or SHA-3 for MACs — never H(key||msg).",
    }


# ---------------------------------------------------------- GCM nonce reuse


def gcm_nonce_reuse(
    known_plaintext: str = "transfer: 100 USD to Bob",
    secret_plaintext: str = "transfer: 900 USD to Eve",  # noqa: S107 — demo default
) -> dict:
    """Two AES-GCM encryptions under the SAME nonce: the keystream repeats,
    so C1 XOR C2 = P1 XOR P2. Knowing one plaintext recovers the other."""
    if len(known_plaintext) != len(secret_plaintext):
        raise InvalidInputError("Plaintexts must be the same length for the XOR demo.")
    key = os.urandom(32)
    nonce = os.urandom(12)  # THE MISTAKE: fixed across both messages

    p1, p2 = known_plaintext.encode(), secret_plaintext.encode()
    c1 = AES.new(key, AES.MODE_GCM, nonce=nonce).encrypt(p1)
    c2 = AES.new(key, AES.MODE_GCM, nonce=nonce).encrypt(p2)

    xor = bytes(a ^ b for a, b in zip(c1, c2, strict=False))
    recovered = bytes(a ^ b for a, b in zip(xor, p1, strict=False))  # attacker knows P1 only

    return {
        "warning": "Attack gallery — AES-GCM nonce reuse leaks plaintexts AND enables tag forgery.",
        "nonce_hex": nonce.hex(),
        "known_plaintext": known_plaintext,
        "secret_plaintext_hidden_from_attacker": True,
        "ciphertext1_hex": c1.hex(),
        "ciphertext2_hex": c2.hex(),
        "xor_hex": xor.hex(),
        "recovered_secret": recovered.decode("utf-8", errors="replace"),
        "observation": (
            "C1 XOR C2 cancelled the keystream entirely, exposing P1 XOR P2. The "
            "attacker, who legitimately knows P1, read the secret P2 without the "
            "key. Worse: nonce reuse in GCM also leaks the auth key, enabling "
            "unlimited tag forgeries."
        ),
        "countermeasure": "Unique nonce per (key, message): random 96-bit nonces or a strict counter.",
    }
