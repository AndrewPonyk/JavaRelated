"""Dev helper: generate synthetic DICOM studies and upload them via STOW-RS.

Uses pydicom to build valid datasets in code — no real PHI. Logs in with the
seeded dev radiologist, then POSTs each instance to the DICOMweb STOW endpoint.
For local/synthetic environments only.

Usage:
    python scripts/seed_sample_dicom.py --count 5 --api http://localhost:8000
"""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.parse
import urllib.request
from io import BytesIO

import numpy as np
import pydicom
from pydicom.dataset import FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian, SecondaryCaptureImageStorage, generate_uid

DEV_USER = "radiologist@medimaging.local"
DEV_PASSWORD = "radiology123"


def build_synthetic_cxr(index: int) -> bytes:
    """Create a synthetic chest X-ray (modality DX) Part-10 byte stream."""
    file_meta = FileMetaDataset()
    file_meta.MediaStorageSOPClassUID = SecondaryCaptureImageStorage
    file_meta.MediaStorageSOPInstanceUID = generate_uid()
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian

    ds = pydicom.Dataset()
    ds.file_meta = file_meta
    ds.StudyInstanceUID = generate_uid()
    ds.SeriesInstanceUID = generate_uid()
    ds.SOPInstanceUID = file_meta.MediaStorageSOPInstanceUID
    ds.SOPClassUID = SecondaryCaptureImageStorage
    ds.PatientID = f"SYN-{index:04d}"
    ds.PatientName = f"Synthetic^Patient{index}"
    ds.Modality = "DX"
    ds.BodyPartExamined = "CHEST"
    ds.StudyDate = "20260101"
    ds.AccessionNumber = f"ACC-{index:05d}"
    ds.InstanceNumber = "1"

    arr = (np.random.default_rng(index).random((64, 64)) * 255).astype(np.uint8)
    ds.Rows, ds.Columns = arr.shape
    ds.SamplesPerPixel = 1
    ds.PhotometricInterpretation = "MONOCHROME2"
    ds.BitsAllocated = 8
    ds.BitsStored = 8
    ds.HighBit = 7
    ds.PixelRepresentation = 0
    ds.PixelData = arr.tobytes()

    buf = BytesIO()
    ds.save_as(buf, write_like_original=False)
    return buf.getvalue()


def login(api: str) -> str:
    data = urllib.parse.urlencode({"username": DEV_USER, "password": DEV_PASSWORD}).encode()
    req = urllib.request.Request(
        f"{api}/api/v1/auth/token",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.load(resp)["access_token"]


def stow(api: str, token: str, dicom_bytes: bytes) -> int:
    req = urllib.request.Request(
        f"{api}/api/v1/dicomweb/studies",
        data=dicom_bytes,
        headers={"Content-Type": "application/dicom", "Authorization": f"Bearer {token}"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.status


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=5)
    parser.add_argument("--api", default="http://localhost:8000")
    args = parser.parse_args()

    try:
        token = login(args.api)
    except urllib.error.URLError as exc:
        raise SystemExit(f"[seed] login failed against {args.api}: {exc}") from exc

    ok = 0
    for i in range(args.count):
        try:
            status = stow(args.api, token, build_synthetic_cxr(i))
            ok += 1
            print(f"[seed] uploaded synthetic study {i} (HTTP {status})")
        except urllib.error.HTTPError as exc:
            print(f"[seed] study {i} failed: HTTP {exc.code}")
    print(f"[seed] done: {ok}/{args.count} uploaded to {args.api}")


if __name__ == "__main__":
    main()
