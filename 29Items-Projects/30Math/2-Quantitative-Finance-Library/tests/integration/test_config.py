"""Configuration contract tests (TECH-NOTES §3.4: `.env.example` is the
authoritative list of every variable — drift fails here)."""

import re
from pathlib import Path

import pytest

from app.config import Settings

pytestmark = pytest.mark.integration

ENV_EXAMPLE = Path(__file__).resolve().parents[2] / ".env.example"


def test_config_documented():
    documented = set(re.findall(r"^#?\s*(QF_[A-Z_]+)=", ENV_EXAMPLE.read_text(), re.M))
    declared = {f"QF_{name.upper()}" for name in Settings.model_fields}
    assert declared - documented == set(), "fields missing from .env.example"
    assert documented - declared == set(), "stale variables in .env.example"


def test_api_key_scope_parsing():
    s = Settings(api_key_hashes="aa,bb:price|fit,cc:admin", _env_file=None)
    table = s.api_keys()
    assert table["aa"] == frozenset({"price", "fit", "admin"})  # bare hash: all scopes
    assert table["bb"] == frozenset({"price", "fit"})
    assert table["cc"] == frozenset({"admin"})


def test_unknown_scope_rejected():
    with pytest.raises(ValueError, match="unknown API-key scopes"):
        Settings(api_key_hashes="aa:launch-missiles", _env_file=None)


def test_rate_limit_parsing():
    s = Settings(rate_limit="10/second", _env_file=None)
    assert s.rate_limit_parsed() == (10, 1.0)
    with pytest.raises(ValueError):
        Settings(rate_limit="often", _env_file=None)
