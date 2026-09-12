"""Instances API: metadata + presigned frame URL, end-to-end via STOW."""

from __future__ import annotations

from tests.conftest import build_dicom

PREFIX = "/api/v1"
DICOM_CT = {"content-type": "application/dicom"}


async def _upload_and_get_sop(client, headers) -> str:
    raw = build_dicom("DX", StudyInstanceUID="ISTU")
    await client.post(f"{PREFIX}/dicomweb/studies", content=raw, headers={**headers, **DICOM_CT})
    listing = await client.get(f"{PREFIX}/studies/ISTU/instances", headers=headers)
    return listing.json()[0]["sop_instance_uid"]


async def test_instance_metadata(client, rad_headers):
    sop = await _upload_and_get_sop(client, rad_headers)
    resp = await client.get(f"{PREFIX}/instances/{sop}", headers=rad_headers)
    assert resp.status_code == 200
    assert resp.json()["sop_instance_uid"] == sop


async def test_frame_url_is_presigned(client, rad_headers):
    sop = await _upload_and_get_sop(client, rad_headers)
    resp = await client.get(f"{PREFIX}/instances/{sop}/frame-url", headers=rad_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["url"].startswith("http")
    assert body["expires_in_seconds"] > 0


async def test_unknown_instance_404(client, rad_headers):
    resp = await client.get(f"{PREFIX}/instances/nope/frame-url", headers=rad_headers)
    assert resp.status_code == 404


async def test_frame_url_requires_auth(client):
    resp = await client.get(f"{PREFIX}/instances/whatever/frame-url")
    assert resp.status_code == 401
