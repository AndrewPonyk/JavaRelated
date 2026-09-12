"""Device registry endpoints (dashboard credential plane)."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Response, status

from app.api.deps import UserContext, get_current_user, require_operator
from app.schemas.device import Device, DeviceCreate, DeviceWithKey
from app.services import device_registry

router = APIRouter()


@router.get("", response_model=list[Device])
async def list_devices(
    _user: Annotated[UserContext, Depends(get_current_user)],
) -> list[Device]:
    return await device_registry.list_all()


@router.post("", status_code=status.HTTP_201_CREATED, response_model=DeviceWithKey)
async def register_device(
    payload: DeviceCreate,
    _user: Annotated[UserContext, Depends(require_operator)],
) -> DeviceWithKey:
    """Register a device. The API key is returned ONCE — only its hash is stored."""
    return await device_registry.register(payload)


@router.get("/{device_id}", response_model=Device)
async def get_device(
    device_id: str,
    _user: Annotated[UserContext, Depends(get_current_user)],
) -> Device:
    device = await device_registry.get(device_id)
    if device is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Device not found")
    return device


@router.patch("/{device_id}/enabled", response_model=dict)
async def set_enabled(
    device_id: str,
    enabled: bool,
    _user: Annotated[UserContext, Depends(require_operator)],
) -> dict:
    """Enable/disable ingest for a device (disabling revokes its key's power)."""
    if await device_registry.get(device_id) is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Device not found")
    await device_registry.set_enabled(device_id, enabled)
    return {"device_id": device_id, "enabled": enabled}


@router.delete("/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_device(
    device_id: str,
    _user: Annotated[UserContext, Depends(require_operator)],
) -> Response:
    # Metric history expires via TTL; only the registry row is removed.
    await device_registry.delete(device_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
