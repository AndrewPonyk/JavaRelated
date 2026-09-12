"""Unit tests for DICOMweb helpers (multipart parsing + DICOM JSON)."""

from __future__ import annotations

import pytest
from app.core.exceptions import InvalidDicomError
from app.services.dicomweb_util import (
    extract_boundary,
    parse_multipart_related,
    reference_sop,
    study_to_dicom_json,
)


def test_extract_boundary():
    assert extract_boundary('multipart/related; boundary="abc123"') == "abc123"
    assert extract_boundary("multipart/related; boundary=xyz") == "xyz"
    assert extract_boundary("application/dicom") is None


def test_parse_single_application_dicom():
    parts = parse_multipart_related(b"RAWDICOM", "application/dicom")
    assert parts == [b"RAWDICOM"]


def test_parse_multipart_related_two_parts():
    boundary = "BND"
    body = (
        (f"--{boundary}\r\n" "Content-Type: application/dicom\r\n\r\n").encode()
        + b"AAA"
        + (f"\r\n--{boundary}\r\n" "Content-Type: application/dicom\r\n\r\n").encode()
        + b"BBB"
        + f"\r\n--{boundary}--\r\n".encode()
    )

    parts = parse_multipart_related(body, f'multipart/related; boundary="{boundary}"')
    assert parts == [b"AAA", b"BBB"]


def test_parse_unsupported_content_type_raises():
    with pytest.raises(InvalidDicomError):
        parse_multipart_related(b"x", "text/plain")


def test_study_to_dicom_json_shape():
    obj = study_to_dicom_json(
        study_instance_uid="1.2.3",
        accession_number="ACC",
        study_date="20260101",
        description="CHEST",
        modalities="DX",
        patient_id="P1",
        patient_name="Doe^John",
        series_count=2,
    )
    assert obj["0020000D"]["Value"] == ["1.2.3"]
    assert obj["00100010"]["Value"] == [{"Alphabetic": "Doe^John"}]
    assert obj["00201206"]["Value"] == [2]


def test_reference_sop():
    item = reference_sop("1", "2", "3", "1.2.840.10008.5.1.4.1.1.7")
    assert item["00081155"]["Value"] == ["3"]
    assert "00081150" in item
