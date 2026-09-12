"""E2E-wide fixtures."""

from __future__ import annotations

from pathlib import Path

import pytest


@pytest.fixture(autouse=True)
def _utf8_write_text(monkeypatch: pytest.MonkeyPatch) -> None:
    """AppTest.from_function writes the extracted page source with the locale
    encoding — cp1252 on Windows — which cannot encode the pages' emoji.
    Force UTF-8 so the suite behaves identically on Windows and Linux CI."""
    original = Path.write_text

    def patched(self, data, encoding=None, errors=None, newline=None):
        return original(self, data, encoding=encoding or "utf-8", errors=errors, newline=newline)

    monkeypatch.setattr(Path, "write_text", patched)
