"""Unit tests for document parsing dispatch (text paths)."""

from __future__ import annotations

from app.rag.parsing import parse_bytes


def test_parse_txt() -> None:
    assert parse_bytes(b"hello world", "notes.txt") == "hello world"


def test_parse_markdown() -> None:
    assert parse_bytes(b"# Title\n\nbody", "README.md").startswith("# Title")


def test_unknown_extension_defaults_to_utf8() -> None:
    assert parse_bytes(b'{"k": 1}', "data.json") == '{"k": 1}'


def test_invalid_utf8_is_replaced_not_raised() -> None:
    # Best-effort decode must not crash on invalid bytes.
    out = parse_bytes(b"\xff\xfe bad bytes", "x.txt")
    assert "bad bytes" in out
