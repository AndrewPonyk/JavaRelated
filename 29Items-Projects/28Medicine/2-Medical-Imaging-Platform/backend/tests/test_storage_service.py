"""StorageService tests against a moto-mocked S3."""

from __future__ import annotations

import pytest
from app.core.config import settings
from app.core.exceptions import NotFoundError


def test_put_get_roundtrip(storage):
    storage.put_object(settings.s3_bucket_dicom, "k/obj.dcm", b"hello", "application/dicom")
    assert storage.get_object(settings.s3_bucket_dicom, "k/obj.dcm") == b"hello"


def test_object_exists(storage):
    assert storage.object_exists(settings.s3_bucket_dicom, "missing") is False
    storage.put_object(settings.s3_bucket_dicom, "present", b"x", "application/dicom")
    assert storage.object_exists(settings.s3_bucket_dicom, "present") is True


def test_get_missing_raises_not_found(storage):
    with pytest.raises(NotFoundError):
        storage.get_object(settings.s3_bucket_dicom, "nope")


def test_delete_object(storage):
    storage.put_object(settings.s3_bucket_dicom, "del", b"x", "application/dicom")
    storage.delete_object(settings.s3_bucket_dicom, "del")
    assert storage.object_exists(settings.s3_bucket_dicom, "del") is False


def test_presigned_get_returns_url(storage):
    storage.put_object(settings.s3_bucket_dicom, "p", b"x", "application/dicom")
    url = storage.presigned_get(settings.s3_bucket_dicom, "p", expires=120)
    assert url.startswith("http") and "p" in url
