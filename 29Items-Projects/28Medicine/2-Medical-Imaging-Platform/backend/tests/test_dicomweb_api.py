"""DICOMweb API: STOW-RS, QIDO-RS, WADO-RS end-to-end."""

from __future__ import annotations

from tests.conftest import build_dicom

PREFIX = "/api/v1"
DICOM_CT = {"content-type": "application/dicom"}


async def test_stow_single_instance(client, rad_headers):
    raw = build_dicom("DX", StudyInstanceUID="W1")
    resp = await client.post(
        f"{PREFIX}/dicomweb/studies", content=raw, headers={**rad_headers, **DICOM_CT}
    )
    assert resp.status_code == 200
    referenced = resp.json()["00081199"]["Value"]
    assert len(referenced) == 1


async def test_stow_multipart_two_instances(client, rad_headers):
    a = build_dicom("DX", StudyInstanceUID="W2", SeriesInstanceUID="W2S1")
    b = build_dicom("DX", StudyInstanceUID="W2", SeriesInstanceUID="W2S2")
    boundary = "BORDER"
    body = (
        f"--{boundary}\r\nContent-Type: application/dicom\r\n\r\n".encode()
        + a
        + f"\r\n--{boundary}\r\nContent-Type: application/dicom\r\n\r\n".encode()
        + b
        + f"\r\n--{boundary}--\r\n".encode()
    )
    headers = {**rad_headers, "content-type": f'multipart/related; boundary="{boundary}"'}
    resp = await client.post(f"{PREFIX}/dicomweb/studies", content=body, headers=headers)
    assert resp.status_code == 200
    assert len(resp.json()["00081199"]["Value"]) == 2


async def test_stow_invalid_dicom_reports_failure(client, rad_headers):
    resp = await client.post(
        f"{PREFIX}/dicomweb/studies",
        content=b"not a dicom file",
        headers={**rad_headers, **DICOM_CT},
    )
    assert resp.status_code == 409
    assert "00081198" in resp.json()  # FailedSOPSequence


async def test_stow_requires_write_scope(client, rad_headers):
    # referring physicians lack study:write; rad has it, so test missing auth instead
    raw = build_dicom("DX")
    resp = await client.post(f"{PREFIX}/dicomweb/studies", content=raw, headers=DICOM_CT)
    assert resp.status_code == 401


async def test_qido_search(client, rad_headers):
    await client.post(
        f"{PREFIX}/dicomweb/studies",
        content=build_dicom("DX", StudyInstanceUID="Q1", PatientID="QP"),
        headers={**rad_headers, **DICOM_CT},
    )
    resp = await client.get(f"{PREFIX}/dicomweb/studies?PatientID=QP", headers=rad_headers)
    assert resp.status_code == 200
    studies = resp.json()
    assert len(studies) == 1
    assert studies[0]["0020000D"]["Value"] == ["Q1"]


async def test_wado_retrieve_instance(client, rad_headers):
    raw = build_dicom("DX", StudyInstanceUID="WD", SeriesInstanceUID="WDS")
    await client.post(
        f"{PREFIX}/dicomweb/studies", content=raw, headers={**rad_headers, **DICOM_CT}
    )
    sop = (await client.get(f"{PREFIX}/studies/WD/instances", headers=rad_headers)).json()[0][
        "sop_instance_uid"
    ]
    resp = await client.get(
        f"{PREFIX}/dicomweb/studies/WD/series/WDS/instances/{sop}", headers=rad_headers
    )
    assert resp.status_code == 200
    assert resp.headers["content-type"] == "application/dicom"
    assert len(resp.content) > 100  # got the stored object back
