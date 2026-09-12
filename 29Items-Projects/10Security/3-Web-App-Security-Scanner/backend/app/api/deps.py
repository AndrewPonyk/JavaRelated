"""Shared FastAPI dependencies + target scoping (the platform's key control)."""

from __future__ import annotations

import asyncio
import ipaddress
import socket
from urllib.parse import urlparse

from fastapi import Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.errors import TargetNotAllowed
from app.db.session import get_session
from app.models.scan import ScanTarget

# Never scannable regardless of allowlist (cloud metadata / loopback)
_DENIED_NETWORKS = [
    ipaddress.ip_network("169.254.0.0/16"),  # link-local + cloud metadata
    ipaddress.ip_network("127.0.0.0/8"),
    ipaddress.ip_network("::1/128"),
    ipaddress.ip_network("0.0.0.0/8"),
]


async def get_db() -> AsyncSession:
    """Alias kept for router readability — the real provider is get_session."""
    return Depends(get_session)


def _reject(host: str, reason: str) -> None:
    raise TargetNotAllowed(f"Target {host!r} rejected: {reason}")


def check_ip_scope(addr: ipaddress.IPv4Address | ipaddress.IPv6Address) -> None:
    """Deny-list + private-range rules for a resolved address."""
    for net in _DENIED_NETWORKS:
        if addr in net:
            _reject(str(addr), "address is on the platform deny list")
    is_restricted = addr.is_private or addr.is_loopback or addr.is_link_local or addr.is_reserved
    if is_restricted and not settings.ALLOW_PRIVATE_TARGETS:
        _reject(str(addr), "private/reserved range requires ALLOW_PRIVATE_TARGETS=true")


async def _resolve_host(host: str) -> list[ipaddress.IPv4Address | ipaddress.IPv6Address]:
    """DNS resolution off the event loop (blocks ~ms)."""

    def _resolve() -> list[str]:
        try:
            infos = socket.getaddrinfo(host, None)
        except socket.gaierror:
            return []
        return list({info[4][0] for info in infos})

    loop = asyncio.get_running_loop()
    return [ipaddress.ip_address(ip) for ip in await loop.run_in_executor(None, _resolve)]


async def target_matches_allowlist(session: AsyncSession, host: str) -> bool:
    """True if host matches any operator-registered pattern.

    Patterns: exact hostname, dotted suffix (sub.example.com), or CIDR.
    An EMPTY allowlist permits any hostname (bootstrap mode) — ranges still
    enforced by check_ip_scope.
    """
    targets = (await session.scalars(select(ScanTarget))).all()
    if not targets:
        return True
    resolved = await _resolve_host(host)
    for target in targets:
        pattern = target.host_pattern.strip().lower()
        host_l = host.lower()
        if pattern.startswith(("http://", "https://")):
            pattern = urlparse(pattern).hostname or pattern
        if host_l == pattern or host_l.endswith("." + pattern):
            return True
        try:
            cidr = ipaddress.ip_network(pattern, strict=False)
        except ValueError:
            continue
        if any(addr in cidr for addr in resolved):
            return True
    return False


async def assert_target_allowed(target_url: str, session: AsyncSession) -> None:
    """Full scope gate — runs BEFORE any scanner sees the URL.

    Layers: URL shape → deny-listed ranges → DNS resolve + range re-check
    (rebinding defense) → operator allowlist (when populated).
    """
    parsed = urlparse(target_url)
    host = parsed.hostname or ""
    if parsed.scheme not in ("http", "https"):
        _reject(host or target_url, "only http(s) targets are supported")
    if not host:
        _reject(target_url, "URL has no host")

    try:
        addr = ipaddress.ip_address(host)
    except ValueError:
        pass
    else:
        check_ip_scope(addr)
        # literal-IP targets must still pass the allowlist when populated
        if not await target_matches_allowlist(session, host):
            _reject(host, "not on the operator allowlist")
        return

    resolved = await _resolve_host(host)
    if not resolved:
        _reject(host, "hostname does not resolve")
    for addr in resolved:
        check_ip_scope(addr)
    if not await target_matches_allowlist(session, host):
        _reject(host, "not on the operator allowlist")
