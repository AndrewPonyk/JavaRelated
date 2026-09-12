"""Shared test fixtures.

Runs the real app against an in-memory SQLite DB (portable models) and a
moto-mocked S3 — so the whole suite executes with no Docker, no Postgres, no
MinIO. Synthetic DICOM datasets are built in code (pydicom); never real PHI.
"""

from __future__ import annotations

import os
from io import BytesIO

# Configure the app for tests BEFORE importing any app module (settings is cached).
os.environ.update(
    APP_ENV="development",
    DATABASE_URL_OVERRIDE="sqlite+aiosqlite://",
    S3_ENDPOINT_URL="",  # empty → treated as real S3 (moto intercepts)
    S3_ACCESS_KEY="testing",
    S3_SECRET_KEY="testing",
    S3_REGION="us-east-1",
    AWS_ACCESS_KEY_ID="testing",
    AWS_SECRET_ACCESS_KEY="testing",
    AWS_DEFAULT_REGION="us-east-1",
    ML_ENABLED="false",
    ML_INLINE="false",  # trigger uses the in-proc queue path (no network)
    INGEST_INLINE="true",
    JWT_SECRET_KEY="test-secret",
)

import pydicom  # noqa: E402
import pytest  # noqa: E402
import pytest_asyncio  # noqa: E402
from app.api import deps  # noqa: E402
from app.core.security import Role, create_access_token  # noqa: E402
from app.main import app  # noqa: E402
from app.models import Base  # noqa: E402
from app.services.storage_service import StorageService  # noqa: E402
from app.services.user_service import UserService  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402
from moto import mock_aws  # noqa: E402
from pydicom.dataset import FileMetaDataset  # noqa: E402
from pydicom.uid import (  # noqa: E402
    ExplicitVRLittleEndian,
    SecondaryCaptureImageStorage,
    generate_uid,
)
from sqlalchemy.ext.asyncio import (  # noqa: E402
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import StaticPool  # noqa: E402


# ── synthetic DICOM builders ─────────────────────────────────
def build_dicom(modality: str = "DX", *, with_pixels: bool = True, **overrides) -> bytes:
    import numpy as np

    fm = FileMetaDataset()
    fm.MediaStorageSOPClassUID = SecondaryCaptureImageStorage
    fm.MediaStorageSOPInstanceUID = generate_uid()
    fm.TransferSyntaxUID = ExplicitVRLittleEndian

    ds = pydicom.Dataset()
    ds.file_meta = fm
    ds.StudyInstanceUID = overrides.get("StudyInstanceUID", generate_uid())
    ds.SeriesInstanceUID = overrides.get("SeriesInstanceUID", generate_uid())
    ds.SOPInstanceUID = fm.MediaStorageSOPInstanceUID
    ds.SOPClassUID = SecondaryCaptureImageStorage
    ds.PatientID = overrides.get("PatientID", "TEST-0001")
    ds.PatientName = overrides.get("PatientName", "Test^Patient")
    ds.Modality = modality
    ds.StudyDate = overrides.get("StudyDate", "20260101")
    ds.AccessionNumber = overrides.get("AccessionNumber", "ACC123")
    ds.InstanceNumber = str(overrides.get("InstanceNumber", 1))
    if with_pixels:
        arr = (np.random.default_rng(1).random((16, 16)) * 255).astype("uint8")
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


@pytest.fixture
def synthetic_dicom_bytes() -> bytes:
    return build_dicom("DX")


@pytest.fixture
def synthetic_ct_bytes() -> bytes:
    return build_dicom("CT")


# ── database ─────────────────────────────────────────────────
@pytest_asyncio.fixture
async def engine():
    eng = create_async_engine(
        "sqlite+aiosqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with eng.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield eng
    await eng.dispose()


@pytest_asyncio.fixture
async def session_factory(engine):
    return async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


@pytest_asyncio.fixture
async def db(session_factory) -> AsyncSession:
    async with session_factory() as session:
        yield session
        await session.commit()


# ── object storage (moto) ────────────────────────────────────
@pytest.fixture
def storage():
    with mock_aws():
        svc = StorageService()
        svc.ensure_buckets()
        yield svc


# ── HTTP client with DI overrides ────────────────────────────
@pytest_asyncio.fixture
async def client(session_factory, storage):
    async def _db_override():
        async with session_factory() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    app.dependency_overrides[deps.db_session] = _db_override
    app.dependency_overrides[deps.get_storage] = lambda: storage

    # Seed default users so /auth/token works.
    async with session_factory() as session:
        svc = UserService(session)
        await svc.create(
            email="admin@example.com", password="admin12345", full_name="Admin", role=Role.ADMIN
        )
        await svc.create(
            email="rad@example.com", password="radpass123", full_name="Rad", role=Role.RADIOLOGIST
        )
        await session.commit()

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture
def admin_headers() -> dict[str, str]:
    return {"Authorization": f"Bearer {create_access_token('admin@example.com', Role.ADMIN)}"}


@pytest.fixture
def rad_headers() -> dict[str, str]:
    return {"Authorization": f"Bearer {create_access_token('rad@example.com', Role.RADIOLOGIST)}"}
