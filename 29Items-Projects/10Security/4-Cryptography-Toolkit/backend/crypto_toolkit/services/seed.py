"""Idempotent lesson seeding — six core lessons, one per topic."""

from __future__ import annotations

from crypto_toolkit.extensions import db
from crypto_toolkit.models import Lesson

LESSONS = [
    {
        "slug": "aes-authenticated-encryption",
        "title": "AES: Modes Matter More Than Keys",
        "topic": "aes",
        "order_index": 1,
        "demo_endpoint": "/api/aes/encrypt",
        "content_md": (
            "AES scrambles **16-byte blocks**, but the *mode of operation* decides whether "
            "your ciphertext keeps secrets.\n\n"
            "- **ECB** encrypts each block independently: identical blocks encrypt identically, "
            "so patterns leak (see the penguin in the Attack Gallery).\n"
            "- **CBC** chains blocks with a random IV — confidential but *unauthenticated*; "
            "an attacker can flip ciphertext bits and flip plaintext bits.\n"
            "- **GCM** is an *AEAD*: confidentiality + integrity in one pass with a 96-bit "
            "nonce and a 128-bit tag. Tampering breaks the tag and decryption refuses.\n\n"
            "**Try it:** encrypt the same sentence twice in GCM — different IVs, different "
            "ciphertexts. Then flip one bit of the ciphertext and watch the tag check fail.\n\n"
            "*Rule: nonce must be unique per key. Reuse it and GCM collapses — the gallery "
            "shows the XOR attack.*"
        ),
    },
    {
        "slug": "rsa-padding-is-the-protocol",
        "title": "RSA: The Padding IS the Protocol",
        "topic": "rsa",
        "order_index": 2,
        "demo_endpoint": "/api/rsa/encrypt",
        "content_md": (
            "Textbook RSA — `c = m^e mod n` — is deterministic and *multiplicatively "
            "malleable*: multiply a ciphertext by `E(2)` and the decrypted plaintext "
            "doubles. An attacker edits your ciphertext without ever holding the key.\n\n"
            "**OAEP** (encryption) randomizes each message before exponentiation and "
            "verifies structure on decrypt. **PSS** (signatures) does the same for "
            "signing. Both need >=2048-bit keys today.\n\n"
            "**Try it:** run the malleability demo in the Attack Gallery, then compare "
            "with the OAEP round-trip here — same modulus size, wildly different "
            "adversary model.\n\n"
            "Real systems rarely RSA-encrypt data directly: message size is capped by "
            "the modulus; hybrid encryption (RSA wraps a fresh AES key) is the answer."
        ),
    },
    {
        "slug": "ecdsa-and-the-nonce",
        "title": "ECDSA: One Bad Nonce Leaks Everything",
        "topic": "ecdsa",
        "order_index": 3,
        "demo_endpoint": "/api/ecdsa/sign",
        "content_md": (
            "ECDSA signatures need a fresh secret `k` per signature. The signature leaks "
            "`r = (k·G).x` — and if `k` ever repeats, two signatures give two equations "
            "in two unknowns (`k` and the private key `d`). Solving them is a weekend "
            "exercise with modular arithmetic — and it sank Sony's PS3 code-signing key "
            "in 2010.\n\n"
            "**RFC 6979** derives `k` deterministically from (key, message), making "
            "reuse impossible even with a broken RNG.\n\n"
            "**Try it:** sign two different messages in the k-reuse demo, press "
            "recover, and watch the private key fall out of the two public signatures."
        ),
    },
    {
        "slug": "sha3-and-the-sponge",
        "title": "SHA-3: Why the Sponge Wins",
        "topic": "sha3",
        "order_index": 4,
        "demo_endpoint": "/api/sha3/digest",
        "content_md": (
            "SHA-2 and SHA-3 are both secure hashes, but their internals differ in a way "
            "that matters for MACs.\n\n"
            "SHA-2 is Merkle–Damgård: its state is 'resumable', so `H(key||msg)` can be "
            "extended by anyone who knows the digest — the length-extension attack in "
            "the gallery.\n\n"
            "SHA-3 is a **sponge** (Keccak): every output bit depends on the entire "
            "state, and the squeezing phase is not invertible into a resumable state. "
            "Length extension simply does not apply.\n\n"
            "**Try it:** hash the same message with SHA3-256/384/512 and compare "
            "digest lengths; then run the length-extension demo to see why the "
            "*construction* (HMAC vs naive concatenation) matters as much as the hash."
        ),
    },
    {
        "slug": "argon2-memory-hard-hashing",
        "title": "Argon2: Make Brute Force Expensive",
        "topic": "argon2",
        "order_index": 5,
        "demo_endpoint": "/api/argon2/hash",
        "content_md": (
            "Password hashing is a different game from encryption: the 'key' is a "
            "low-entropy human password, so the defence is *cost*.\n\n"
            "Argon2id is memory-hard: it burns time AND RAM, so a GPU rig (fast cores, "
            "little memory per core) gains almost nothing over a CPU.\n\n"
            "Parameters are a budget decision: `interactive` (~50 ms, 19 MiB) for "
            "login flows, `moderate` for server-side accounts, `paranoid` for "
            "cold-storage-grade secrets.\n\n"
            "**Try it:** hash a password at each preset and feel the wall-clock "
            "difference. Note the encoded hash embeds its own parameters — that's "
            "how verification knows what to re-run."
        ),
    },
    {
        "slug": "tls13-handshake-walkthrough",
        "title": "TLS 1.3: The Handshake, Byte by Byte",
        "topic": "tls",
        "order_index": 6,
        "demo_endpoint": "/api/tls13/handshake",
        "content_md": (
            "TLS 1.3 cut the handshake to **1-RTT**: the key exchange starts in the "
            "ClientHello itself.\n\n"
            "Walk the steps: X25519 key shares in the clear → HKDF key schedule with "
            "labelled derivations → everything after ServerHello encrypted → "
            "transcript-MAC Finished messages both ways → application traffic secrets.\n\n"
            "Every value in the walkthrough is *real crypto* — but simplified framing. "
            "For the genuine article, open devtools on this site: the edge nginx "
            "speaks TLS 1.3 only, and `openssl s_client -connect host:443 -tls1_3` "
            "shows the live negotiation.\n\n"
            "The downgrade demo explains why stripping attacks now fail: the "
            "'DOWNGRD' sentinel in ServerHello.random, encrypted negotiation, HSTS."
        ),
    },
]


def seed_if_empty() -> int:
    """Insert the six core lessons when the table is empty. Returns count added."""
    if Lesson.query.count():
        return 0
    for entry in LESSONS:
        db.session.add(Lesson(**entry))
    db.session.commit()
    return len(LESSONS)
