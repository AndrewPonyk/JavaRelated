"""DICOMweb helpers: multipart/related parsing and DICOM JSON (PS3.18) output."""

from __future__ import annotations

from typing import Any

from app.core.exceptions import InvalidDicomError

# DICOM JSON objects are tag-keyed dicts with mixed value shapes.
DicomJson = dict[str, Any]


def extract_boundary(content_type: str) -> str | None:
    """Pull the boundary token out of a multipart Content-Type header."""
    for part in content_type.split(";"):
        part = part.strip()
        if part.lower().startswith("boundary="):
            return part[len("boundary=") :].strip().strip('"')
    return None


def parse_multipart_related(body: bytes, content_type: str) -> list[bytes]:
    """Split a multipart/related body into its part payloads.

    Also accepts a single raw `application/dicom` body (returns one part).
    """
    ctype = content_type.lower()
    if "multipart/related" not in ctype:
        if "application/dicom" in ctype:
            return [body]
        raise InvalidDicomError(f"Unsupported Content-Type: {content_type}")

    boundary = extract_boundary(content_type)
    if not boundary:
        raise InvalidDicomError("Missing multipart boundary")

    delimiter = b"--" + boundary.encode()
    segments = body.split(delimiter)
    parts: list[bytes] = []
    for seg in segments:
        # Skip the preamble and the closing "--" terminator / empty segments.
        if not seg or seg in (b"--", b"--\r\n", b"\r\n"):
            continue
        seg = seg.lstrip(b"\r\n")
        header_end = seg.find(b"\r\n\r\n")
        if header_end == -1:
            continue
        payload = seg[header_end + 4 :]
        payload = payload.rstrip(b"\r\n")  # trim trailing CRLF before next boundary
        if payload:
            parts.append(payload)
    if not parts:
        raise InvalidDicomError("No DICOM parts found in multipart body")
    return parts


# ── DICOM JSON serialization (subset of attributes) ──────────
def _tag(value: str | None, vr: str) -> DicomJson | None:
    if value in (None, ""):
        return {"vr": vr}
    if vr == "PN":
        return {"vr": vr, "Value": [{"Alphabetic": str(value)}]}
    return {"vr": vr, "Value": [value]}


def study_to_dicom_json(
    *,
    study_instance_uid: str,
    accession_number: str | None,
    study_date: str | None,
    description: str | None,
    modalities: str | None,
    patient_id: str | None,
    patient_name: str | None,
    series_count: int,
) -> DicomJson:
    """Serialize a study row to the DICOM JSON model used by QIDO-RS."""
    obj = {
        "0020000D": _tag(study_instance_uid, "UI"),  # StudyInstanceUID
        "00080050": _tag(accession_number, "SH"),  # AccessionNumber
        "00080020": _tag(study_date, "DA"),  # StudyDate
        "00081030": _tag(description, "LO"),  # StudyDescription
        "00080061": _tag(modalities, "CS"),  # ModalitiesInStudy
        "00100020": _tag(patient_id, "LO"),  # PatientID
        "00100010": _tag(patient_name, "PN"),  # PatientName
        "00201206": {"vr": "IS", "Value": [series_count]},  # NumberOfStudyRelatedSeries
    }
    return {k: v for k, v in obj.items() if v is not None}


def reference_sop(
    study_uid: str, series_uid: str, sop_uid: str, sop_class: str | None
) -> DicomJson:
    """A ReferencedSOPSequence item for the STOW-RS response."""
    item = {
        "00081155": {"vr": "UI", "Value": [sop_uid]},  # ReferencedSOPInstanceUID
    }
    if sop_class:
        item["00081150"] = {"vr": "UI", "Value": [sop_class]}  # ReferencedSOPClassUID
    return item
