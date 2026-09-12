"""DICOM parsing & metadata extraction (pydicom).

Framework-agnostic and pure where possible so it is trivially unit-testable with
synthetic datasets (`pydicom.Dataset` built in code) — no real PHI needed.

Key rule: this module reads *metadata*; it does NOT touch `PixelData` in the
request path. Pixel decoding belongs to WADO-RS / ML workers.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import BinaryIO

import pydicom
from pydicom.errors import InvalidDicomError as PydicomInvalidError

from app.core.exceptions import InvalidDicomError

# Mandatory tags without which we cannot place an instance in the hierarchy.
_REQUIRED_TAGS = ("StudyInstanceUID", "SeriesInstanceUID", "SOPInstanceUID")

# Modalities we route to the chest X-ray classifier.
CHEST_XRAY_MODALITIES = frozenset({"CR", "DX"})


@dataclass(slots=True)
class DicomMetadata:
    """Flattened, storage-ready view of a DICOM object's key attributes."""

    study_instance_uid: str
    series_instance_uid: str
    sop_instance_uid: str
    sop_class_uid: str | None = None
    patient_id: str | None = None
    patient_name: str | None = None
    patient_birth_date: str | None = None
    patient_sex: str | None = None
    accession_number: str | None = None
    study_date: str | None = None
    study_time: str | None = None
    study_description: str | None = None
    modality: str | None = None
    series_number: int | None = None
    series_description: str | None = None
    body_part: str | None = None
    instance_number: int | None = None
    transfer_syntax_uid: str | None = None
    rows: int | None = None
    columns: int | None = None
    extra: dict[str, str] = field(default_factory=dict)

    @property
    def is_chest_xray(self) -> bool:
        return (self.modality or "").upper() in CHEST_XRAY_MODALITIES


def _get(ds: pydicom.Dataset, name: str) -> str | None:
    value = ds.get(name, None)
    return str(value) if value not in (None, "") else None


def _get_int(ds: pydicom.Dataset, name: str) -> int | None:
    value = ds.get(name, None)
    try:
        return int(value) if value not in (None, "") else None
    except (TypeError, ValueError):
        return None


def parse_metadata(source: BinaryIO | str) -> DicomMetadata:
    """Read a DICOM Part-10 stream/path and extract metadata.

    `stop_before_pixels=True` avoids loading large PixelData — fast and memory
    cheap. Raises `InvalidDicomError` on anything unparseable or missing a
    mandatory UID.
    """
    try:
        ds = pydicom.dcmread(source, stop_before_pixels=True, force=False)
    except (PydicomInvalidError, OSError, ValueError) as exc:
        raise InvalidDicomError(f"Unreadable DICOM: {exc}") from exc

    for tag in _REQUIRED_TAGS:
        if not ds.get(tag, None):
            raise InvalidDicomError(f"Missing mandatory tag: {tag}")

    transfer_syntax = None
    if "TransferSyntaxUID" in getattr(ds, "file_meta", {}):
        transfer_syntax = str(ds.file_meta.TransferSyntaxUID)

    return DicomMetadata(
        study_instance_uid=str(ds.StudyInstanceUID),
        series_instance_uid=str(ds.SeriesInstanceUID),
        sop_instance_uid=str(ds.SOPInstanceUID),
        sop_class_uid=_get(ds, "SOPClassUID"),
        patient_id=_get(ds, "PatientID"),
        patient_name=_get(ds, "PatientName"),
        patient_birth_date=_get(ds, "PatientBirthDate"),
        patient_sex=_get(ds, "PatientSex"),
        accession_number=_get(ds, "AccessionNumber"),
        study_date=_get(ds, "StudyDate"),
        study_time=_get(ds, "StudyTime"),
        study_description=_get(ds, "StudyDescription"),
        modality=_get(ds, "Modality"),
        series_number=_get_int(ds, "SeriesNumber"),
        series_description=_get(ds, "SeriesDescription"),
        body_part=_get(ds, "BodyPartExamined"),
        instance_number=_get_int(ds, "InstanceNumber"),
        transfer_syntax_uid=transfer_syntax,
        rows=_get_int(ds, "Rows"),
        columns=_get_int(ds, "Columns"),
    )


def build_object_key(meta: DicomMetadata) -> str:
    """Deterministic archive key: archive/{study}/{series}/{sop}.dcm.

    Deterministic on UIDs → ingestion is idempotent (re-upload overwrites the
    same key instead of duplicating).
    """
    return (
        f"archive/{meta.study_instance_uid}/"
        f"{meta.series_instance_uid}/{meta.sop_instance_uid}.dcm"
    )
