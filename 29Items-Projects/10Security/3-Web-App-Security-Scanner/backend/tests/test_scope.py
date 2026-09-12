"""Unit tests: SSRF/scope enforcement (app.api.deps.assert_target_allowed).

DNS resolution is monkeypatched — these tests are hermetic.
"""

from __future__ import annotations

import ipaddress

import pytest
from app.api import deps
from app.api.deps import assert_target_allowed, check_ip_scope, target_matches_allowlist
from app.core.errors import TargetNotAllowed
from app.models.scan import ScanTarget

PUBLIC_IP = ipaddress.ip_address("93.184.216.34")


@pytest.fixture
def resolve_public(monkeypatch):
    """Map every hostname to a public IP."""

    async def _fake(host: str):
        return [PUBLIC_IP]

    monkeypatch.setattr(deps, "_resolve_host", _fake)


async def seed_target(session_factory, pattern: str) -> None:
    async with session_factory() as session:
        session.add(ScanTarget(host_pattern=pattern))
        await session.commit()


# ── direct range checks ────────────────────────────────────────────────


@pytest.mark.parametrize("ip", ["169.254.169.254", "127.0.0.1", "0.0.0.5"])
def test_deny_list_always_rejected(ip):
    with pytest.raises(TargetNotAllowed, match="deny list"):
        check_ip_scope(ipaddress.ip_address(ip))


def test_private_rejected_by_default():
    with pytest.raises(TargetNotAllowed, match="ALLOW_PRIVATE_TARGETS"):
        check_ip_scope(ipaddress.ip_address("10.1.2.3"))


def test_private_allowed_when_configured(monkeypatch):
    monkeypatch.setattr(deps.settings, "ALLOW_PRIVATE_TARGETS", True)
    check_ip_scope(ipaddress.ip_address("10.1.2.3"))  # no raise
    # deny-list still wins even with the flag on
    with pytest.raises(TargetNotAllowed):
        check_ip_scope(ipaddress.ip_address("169.254.169.254"))


# ── full URL gate (bootstrap mode: empty allowlist) ────────────────────


async def test_public_target_allowed_bootstrap(db_session, resolve_public):
    await assert_target_allowed("https://app.example.com/login", db_session)


@pytest.mark.parametrize(
    "url",
    [
        "ftp://app.example.com",  # non-http scheme
        "http://",  # no host
        "https://169.254.169.254/latest",  # literal metadata IP
        "http://127.0.0.1:8080/",  # literal loopback
    ],
)
async def test_rejected_urls(db_session, resolve_public, url):
    with pytest.raises(TargetNotAllowed):
        await assert_target_allowed(url, db_session)


async def test_unresolvable_host_rejected(db_session, monkeypatch):
    async def _empty(host: str):
        return []

    monkeypatch.setattr(deps, "_resolve_host", _empty)
    with pytest.raises(TargetNotAllowed, match="does not resolve"):
        await assert_target_allowed("http://nx.example.com/", db_session)


async def test_dns_rebinding_to_private_rejected(db_session, monkeypatch):
    """Hostname resolves public AND private — every address must pass."""

    async def _mixed(host: str):
        return [PUBLIC_IP, ipaddress.ip_address("192.168.0.10")]

    monkeypatch.setattr(deps, "_resolve_host", _mixed)
    with pytest.raises(TargetNotAllowed, match="private"):
        await assert_target_allowed("http://rebind.example.com/", db_session)


# ── allowlist matching (non-empty allowlist) ───────────────────────────


async def test_exact_and_suffix_match(db_session, resolve_public):
    await seed_target_session(db_session, "app.example.com")
    assert await target_matches_allowlist(db_session, "app.example.com")
    assert await target_matches_allowlist(db_session, "www.app.example.com")
    assert not await target_matches_allowlist(db_session, "evil-app.example.com")
    assert not await target_matches_allowlist(db_session, "other.net")


async def seed_target_session(session, pattern):
    session.add(ScanTarget(host_pattern=pattern))
    await session.commit()


async def test_cidr_match(db_session, monkeypatch):
    async def _private(host: str):
        # resolve hostnames into the allowlisted range; literal IPs resolve
        # to themselves so out-of-range literals fail the CIDR check
        try:
            return [ipaddress.ip_address(host)]
        except ValueError:
            return [ipaddress.ip_address("10.0.4.7")]

    monkeypatch.setattr(deps, "_resolve_host", _private)
    monkeypatch.setattr(deps.settings, "ALLOW_PRIVATE_TARGETS", True)
    await seed_target_session(db_session, "10.0.4.0/24")
    assert await target_matches_allowlist(db_session, "internal.corp")
    assert not await target_matches_allowlist(db_session, "10.0.5.7")


async def test_allowlist_blocks_public_host(db_session, resolve_public):
    await seed_target_session(db_session, "allowed.example.com")
    with pytest.raises(TargetNotAllowed, match="allowlist"):
        await assert_target_allowed("https://blocked.example.com/", db_session)


async def test_url_pattern_in_allowlist_normalized(db_session, resolve_public):
    """Operators sometimes paste full URLs — the matcher strips the scheme."""
    await seed_target_session(db_session, "https://app.example.com")
    assert await target_matches_allowlist(db_session, "app.example.com")
