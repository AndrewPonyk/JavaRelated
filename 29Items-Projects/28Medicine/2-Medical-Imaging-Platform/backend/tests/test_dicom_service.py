"""Unit tests for DICOM metadata extraction (no I/O, no PHI)."""

from __future__ import annotations

from io import BytesIO

import pytest
from app.core.exceptions import InvalidDicomError
from app.services.dicom_service import build_object_key, parse_metadata


def test_parse_metadata_extracts_uids(synthetic_dicom_bytes: bytes) -> None:
    meta = parse_metadata(BytesIO(synthetic_dicom_bytes))
    assert meta.study_instance_uid
    assert meta.series_instance_uid
    assert meta.sop_instance_uid
    assert meta.modality == "DX"
    assert meta.rows == 16
    assert meta.columns == 16


def test_chest_xray_detection(synthetic_dicom_bytes: bytes, synthetic_ct_bytes: bytes) -> None:
    assert parse_metadata(BytesIO(synthetic_dicom_bytes)).is_chest_xray is True
    assert parse_metadata(BytesIO(synthetic_ct_bytes)).is_chest_xray is False


def test_object_key_is_deterministic(synthetic_dicom_bytes: bytes) -> None:
    meta = parse_metadata(BytesIO(synthetic_dicom_bytes))
    key1 = build_object_key(meta)
    key2 = build_object_key(meta)
    assert key1 == key2  # idempotency: same UIDs → same key
    assert key1.startswith("archive/")
    assert key1.endswith(f"{meta.sop_instance_uid}.dcm")


def test_invalid_payload_raises() -> None:
    with pytest.raises(InvalidDicomError):
        parse_metadata(BytesIO(b"this is not a DICOM file"))
