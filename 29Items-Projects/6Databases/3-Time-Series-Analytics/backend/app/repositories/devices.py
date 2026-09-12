"""Data access for the device registry (single-partition-per-device table)."""

from __future__ import annotations

from datetime import UTC, datetime

from app.db import cassandra
from app.schemas.device import Device

_INSERT = (
    "INSERT INTO devices (device_id, name, site, device_type, api_key_hash, enabled, created_at) "
    "VALUES (?, ?, ?, ?, ?, ?, ?)"
)
_SELECT_ONE = "SELECT * FROM devices WHERE device_id = ?"
# Full scan of a small registry table (one row per device). Scale note in
# docs/PROJECT-PLAN.md: introduce devices_by_site when the fleet outgrows this.
_SELECT_ALL = "SELECT * FROM devices"
_SET_ENABLED = "UPDATE devices SET enabled = ? WHERE device_id = ?"
_DELETE = "DELETE FROM devices WHERE device_id = ?"


def _to_device(row) -> Device:
    return Device(
        device_id=row.device_id,
        name=row.name,
        site=row.site or "default",
        device_type=row.device_type or "generic",
        enabled=bool(row.enabled),
        created_at=row.created_at.replace(tzinfo=UTC) if row.created_at else None,
    )


async def insert(device: Device, api_key_hash: str) -> None:
    await cassandra.execute(
        _INSERT,
        (
            device.device_id,
            device.name,
            device.site,
            device.device_type,
            api_key_hash,
            device.enabled,
            datetime.now(UTC),
        ),
    )


async def get(device_id: str) -> Device | None:
    rows = await cassandra.execute(_SELECT_ONE, (device_id,))
    row = rows.one()
    return _to_device(row) if row else None


async def get_api_key_hash(device_id: str) -> str | None:
    rows = await cassandra.execute(_SELECT_ONE, (device_id,))
    row = rows.one()
    return row.api_key_hash if row else None


async def list_all() -> list[Device]:
    rows = await cassandra.execute(_SELECT_ALL)
    return [_to_device(r) for r in rows]


async def set_enabled(device_id: str, enabled: bool) -> None:
    await cassandra.execute(_SET_ENABLED, (enabled, device_id))


async def delete(device_id: str) -> None:
    await cassandra.execute(_DELETE, (device_id,))
