"""Data access for dashboard users."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import NamedTuple

from app.db import cassandra

_INSERT_IF_ABSENT = (
    "INSERT INTO users (username, password_hash, roles, created_at) "
    "VALUES (?, ?, ?, ?) IF NOT EXISTS"
)
_SELECT = "SELECT username, password_hash, roles FROM users WHERE username = ?"


class UserRecord(NamedTuple):
    username: str
    password_hash: str
    roles: list[str]


async def get(username: str) -> UserRecord | None:
    rows = await cassandra.execute(_SELECT, (username,))
    row = rows.one()
    if row is None:
        return None
    return UserRecord(
        username=row.username,
        password_hash=row.password_hash,
        roles=sorted(row.roles or []),
    )


async def create_if_absent(username: str, password_hash: str, roles: list[str]) -> bool:
    """Lightweight-transaction insert; returns True if the user was created."""
    rows = await cassandra.execute(
        _INSERT_IF_ABSENT,
        (username, password_hash, set(roles), datetime.now(UTC)),
    )
    return bool(rows.one().applied)
