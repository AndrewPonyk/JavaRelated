"""De-identification (DICOM PS3.15 Annex E basic profile, pragmatic subset).

Applied on research/training data paths (`DEIDENTIFY_ON_INGEST=true`). Blanks
direct-identifier tags, drops private tags, and marks the dataset as
de-identified. UIDs are intentionally preserved so archive idempotency and the
Study/Series/Instance linkage hold.

Scope note: detection/removal of *burned-in pixel annotations* (PS3.15 requires
considering these) is a separate pixel-processing concern handled by the export
pipeline and is out of scope for this metadata-level pass.
"""

from __future__ import annotations

import pydicom

# Direct-identifier tags blanked by the basic profile (pragmatic subset).
_TAGS_TO_BLANK = (
    "PatientName",
    "PatientID",
    "PatientBirthDate",
    "PatientBirthTime",
    "PatientAddress",
    "PatientTelephoneNumbers",
    "OtherPatientIDs",
    "OtherPatientNames",
    "ReferringPhysicianName",
    "PerformingPhysicianName",
    "OperatorsName",
    "InstitutionName",
    "InstitutionAddress",
    "StationName",
    "AccessionNumber",
)


def deidentify(ds: pydicom.Dataset) -> pydicom.Dataset:
    """Return a de-identified dataset (mutated in place and returned)."""
    for tag in _TAGS_TO_BLANK:
        if tag in ds:
            ds[tag].value = ""

    # Remove all private (odd-group) tags — common PHI hiding spot.
    ds.remove_private_tags()

    # Mark provenance per PS3.15.
    ds.PatientIdentityRemoved = "YES"
    ds.DeidentificationMethod = "MEDIMAGING-BASIC-SUBSET"
    return ds
