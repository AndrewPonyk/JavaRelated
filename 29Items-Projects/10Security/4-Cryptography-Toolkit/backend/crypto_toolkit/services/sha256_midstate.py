"""SHA-256 with injectable state — the tool that makes length extension work.

hashlib deliberately does not expose midstates, because resuming from a
digest is exactly what breaks naive MAC constructions SHA256(key||msg).
For the lesson we implement the compression function so students can see
the attack perform a real forgery, not a diagram of one.
"""

from __future__ import annotations

import hashlib
import struct

_K = [
    0x428A2F98,
    0x71374491,
    0xB5C0FBCF,
    0xE9B5DBA5,
    0x3956C25B,
    0x59F111F1,
    0x923F82A4,
    0xAB1C5ED5,
    0xD807AA98,
    0x12835B01,
    0x243185BE,
    0x550C7DC3,
    0x72BE5D74,
    0x80DEB1FE,
    0x9BDC06A7,
    0xC19BF174,
    0xE49B69C1,
    0xEFBE4786,
    0x0FC19DC6,
    0x240CA1CC,
    0x2DE92C6F,
    0x4A7484AA,
    0x5CB0A9DC,
    0x76F988DA,
    0x983E5152,
    0xA831C66D,
    0xB00327C8,
    0xBF597FC7,
    0xC6E00BF3,
    0xD5A79147,
    0x06CA6351,
    0x14292967,
    0x27B70A85,
    0x2E1B2138,
    0x4D2C6DFC,
    0x53380D13,
    0x650A7354,
    0x766A0ABB,
    0x81C2C92E,
    0x92722C85,
    0xA2BFE8A1,
    0xA81A664B,
    0xC24B8B70,
    0xC76C51A3,
    0xD192E819,
    0xD6990624,
    0xF40E3585,
    0x106AA070,
    0x19A4C116,
    0x1E376C08,
    0x2748774C,
    0x34B0BCB5,
    0x391C0CB3,
    0x4ED8AA4A,
    0x5B9CCA4F,
    0x682E6FF3,
    0x748F82EE,
    0x78A5636F,
    0x84C87814,
    0x8CC70208,
    0x90BEFFFA,
    0xA4506CEB,
    0xBEF9A3F7,
    0xC67178F2,
]
_MASK = 0xFFFFFFFF


def _rotr(x: int, n: int) -> int:
    return ((x >> n) | (x << (32 - n))) & _MASK


def _compress(state: list, block: bytes) -> list:
    w = list(struct.unpack(">16I", block))
    for t in range(16, 64):
        s0 = _rotr(w[t - 15], 7) ^ _rotr(w[t - 15], 18) ^ (w[t - 15] >> 3)
        s1 = _rotr(w[t - 2], 17) ^ _rotr(w[t - 2], 19) ^ (w[t - 2] >> 10)
        w.append((w[t - 16] + s0 + w[t - 7] + s1) & _MASK)

    a, b, c, d, e, f, g, h = state
    for t in range(64):
        S1 = _rotr(e, 6) ^ _rotr(e, 11) ^ _rotr(e, 25)
        ch = (e & f) ^ (~e & g)
        t1 = (h + S1 + ch + _K[t] + w[t]) & _MASK
        S0 = _rotr(a, 2) ^ _rotr(a, 13) ^ _rotr(a, 22)
        maj = (a & b) ^ (a & c) ^ (b & c)
        t2 = (S0 + maj) & _MASK
        h, g, f, e = g, f, e, (d + t1) & _MASK
        d, c, b, a = c, b, a, (t1 + t2) & _MASK

    return [(s + v) & _MASK for s, v in zip(state, [a, b, c, d, e, f, g, h], strict=False)]


def md_padding(total_len: int) -> bytes:
    """Merkle–Damgård padding for a message of total_len bytes."""
    pad_len = (55 - total_len) % 64 + 1  # 0x80 then zeros then 8 length bytes
    return b"\x80" + b"\x00" * (pad_len - 1) + struct.pack(">Q", total_len * 8)


def digest_from_state(state: bytes, data: bytes, prefix_len: int) -> bytes:
    """Hash `data` starting from an already-computed 32-byte `state`.

    `prefix_len` is the total byte length of the (padded) message that
    produced `state` — required to append correct length encoding.
    """
    if len(state) != 32:
        raise ValueError("state must be a 32-byte SHA-256 digest.")
    h = list(struct.unpack(">8I", state))
    total = prefix_len + len(data)
    buf = data + md_padding(total)
    for off in range(0, len(buf), 64):
        h = _compress(h, buf[off : off + 64])
    return struct.pack(">8I", *h)


def sha256(data: bytes) -> bytes:
    """Ordinary SHA-256 via the same code path (test vector parity with hashlib)."""
    h = [0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19]
    buf = data + md_padding(len(data))
    for off in range(0, len(buf), 64):
        h = _compress(h, buf[off : off + 64])
    return struct.pack(">8I", *h)


# Import-time parity self-test: if this pure-Python SHA-256 ever drifts from
# hashlib, every length-extension forgery would silently break — fail at boot.
if hashlib.sha256(b"self-test").digest() != sha256(b"self-test"):
    raise RuntimeError("sha256_midstate failed its hashlib parity self-test")
