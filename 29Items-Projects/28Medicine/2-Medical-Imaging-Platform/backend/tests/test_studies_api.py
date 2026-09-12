"""Studies API: auth, RBAC, validation, and end-to-end via STOW upload."""

from __future__ import annotations

from tests.conftest import build_dicom

PREFIX = "/api/v1"
DICOM_CT = {"content-type": "application/dicom"}


async def _stow(client, headers, raw: bytes):
    return await client.post(
        f"{PREFIX}/dicomweb/studies", content=raw, headers={**headers, **DICOM_CT}
    )


async def test_health_live_is_public(client):
    resp = await client.get(f"{PREFIX}/health/live")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


async def test_list_studies_requires_auth(client):
    resp = await client.get(f"{PREFIX}/studies")
    assert resp.status_code == 401


async def test_list_studies_empty(client, rad_headers):
    resp = await client.get(f"{PREFIX}/studies", headers=rad_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["meta"]["total"] == 0
    assert body["items"] == []


async def test_invalid_query_param_rejected(client, rad_headers):
    resp = await client.get(f"{PREFIX}/studies?limit=9999", headers=rad_headers)
    assert resp.status_code == 422


async def test_upload_then_list_and_get(client, rad_headers):
    raw = build_dicom("DX", StudyInstanceUID="STU-1", PatientID="PX")
    up = await _stow(client, rad_headers, raw)
    assert up.status_code == 200

    listing = await client.get(f"{PREFIX}/studies", headers=rad_headers)
    assert listing.status_code == 200
    items = listing.json()["items"]
    assert len(items) == 1
    assert items[0]["study_instance_uid"] == "STU-1"
    assert items[0]["series_count"] == 1

    got = await client.get(f"{PREFIX}/studies/STU-1", headers=rad_headers)
    assert got.status_code == 200
    assert got.json()["study_instance_uid"] == "STU-1"


async def test_get_unknown_study_404(client, rad_headers):
    resp = await client.get(f"{PREFIX}/studies/nope", headers=rad_headers)
    assert resp.status_code == 404


async def test_study_series_and_instances(client, rad_headers):
    raw = build_dicom("DX", StudyInstanceUID="STU-2", SeriesInstanceUID="SER-2")
    await _stow(client, rad_headers, raw)

    series = await client.get(f"{PREFIX}/studies/STU-2/series", headers=rad_headers)
    assert series.status_code == 200
    assert series.json()[0]["series_instance_uid"] == "SER-2"

    instances = await client.get(f"{PREFIX}/studies/STU-2/instances", headers=rad_headers)
    assert instances.status_code == 200
    assert len(instances.json()) == 1
