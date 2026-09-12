"""Datasets: bundled demo data, upload validation, and library persistence.

Saved datasets are parquet blobs in PostgreSQL, deduplicated by sha256 content
hash and size-capped by ``max_upload_mb``. In demo mode every persistence call
is a logged no-op — analysis never depends on storage (ARCHITECTURE §2.6).
"""

from __future__ import annotations

import hashlib
import io
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd

from app.core.config import get_settings
from app.core.errors import DataValidationError, StorageError
from app.core.logging import get_logger

logger = get_logger(__name__)

SAMPLES_DIR = Path(__file__).resolve().parents[2] / "data" / "samples"


@dataclass(frozen=True)
class SavedDatasetInfo:
    id: str
    name: str
    source_type: str
    row_count: int
    column_count: int
    created_at: str  # ISO — plain data so the UI never holds detached ORM objects


def list_demo_datasets() -> list[str]:
    if not SAMPLES_DIR.exists():
        return []
    return sorted(p.stem for p in SAMPLES_DIR.glob("*.csv"))


def load_demo(name: str) -> pd.DataFrame:
    path = (SAMPLES_DIR / f"{name}.csv").resolve()
    # Path-traversal guard: the resolved file must stay inside data/samples.
    if SAMPLES_DIR.resolve() not in path.parents or not path.exists():
        raise DataValidationError(
            f"unknown demo dataset: {name!r}", user_message="Unknown demo dataset."
        )
    return pd.read_csv(path)


def read_upload(file: Any) -> pd.DataFrame:
    """Validate & parse an uploaded CSV (``file``: object with .name/.size/.read).

    All boundary validation happens here so inner layers can assume a
    well-formed frame (docs/ARCHITECTURE.md §2.3).
    """
    settings = get_settings()
    if not str(file.name).lower().endswith(".csv"):
        raise DataValidationError(
            f"unsupported extension: {file.name}",
            user_message="Only .csv files are supported.",
        )
    size = getattr(file, "size", None)
    if size is not None and size > settings.max_upload_mb * 1024 * 1024:
        raise DataValidationError(
            f"upload too large: {size} bytes",
            user_message=f"The file exceeds the {settings.max_upload_mb} MB limit.",
        )
    try:
        df = pd.read_csv(file)
    except Exception as exc:
        raise DataValidationError(
            f"CSV parse failed: {exc}",
            user_message="Could not parse the CSV — check the delimiter and encoding.",
        ) from exc
    if df.empty or df.shape[1] < 2:
        raise DataValidationError(
            f"unusable shape: {df.shape}",
            user_message="The file needs at least 2 columns and 1 data row.",
        )
    logger.info("Upload accepted: %s shape=%s", file.name, df.shape)
    return df


def _to_parquet_bytes(df: pd.DataFrame) -> bytes:
    buffer = io.BytesIO()
    df.to_parquet(buffer, index=False)
    return buffer.getvalue()


def content_hash(df: pd.DataFrame) -> str:
    """sha256 of the parquet payload — the dataset's dedup key."""
    return hashlib.sha256(_to_parquet_bytes(df)).hexdigest()


def save_dataset(
    df: pd.DataFrame,
    name: str,
    *,
    description: str | None = None,
    created_by_email: str | None = None,
    source_type: str = "upload",
) -> str:
    """Persist a dataset (dedup by content hash). No-op in demo mode; returns id or ''."""
    settings = get_settings()
    if settings.demo_mode:
        logger.info("Demo mode — dataset '%s' not persisted", name)
        return ""

    from app.data.db import session_scope
    from app.data.models import Dataset
    from app.data.repositories import DatasetRepository
    from app.stats.profiler import profile_dataframe

    payload = _to_parquet_bytes(df)
    max_bytes = settings.max_upload_mb * 1024 * 1024
    if len(payload) > max_bytes:
        raise DataValidationError(
            f"payload {len(payload)} bytes exceeds cap",
            user_message=f"The dataset exceeds the {settings.max_upload_mb} MB storage cap.",
        )
    digest = hashlib.sha256(payload).hexdigest()
    schema = {p.name: p.kind.value for p in profile_dataframe(df)}

    with session_scope() as session:
        repo = DatasetRepository(session)
        existing = repo.get_by_content_hash(digest)
        if existing is not None:
            logger.info("Dataset '%s' already saved as %s (hash match)", name, existing.id)
            return existing.id
        dataset = repo.add(
            Dataset(
                name=name,
                description=description,
                source_type=source_type,
                row_count=int(len(df)),
                column_count=int(df.shape[1]),
                schema_json=schema,
                payload_parquet=payload,
                content_hash=digest,
                created_by_email=created_by_email,
            )
        )
        dataset_id = dataset.id
    logger.info("Dataset persisted: %s (%s)", name, dataset_id)
    return dataset_id


def list_saved(limit: int = 50) -> list[SavedDatasetInfo]:
    """Saved datasets, newest first. Empty in demo mode."""
    if get_settings().demo_mode:
        return []
    from app.data.db import session_scope
    from app.data.repositories import DatasetRepository

    with session_scope() as session:
        return [
            SavedDatasetInfo(
                id=d.id,
                name=d.name,
                source_type=d.source_type,
                row_count=d.row_count,
                column_count=d.column_count,
                created_at=d.created_at.isoformat() if d.created_at else "",
            )
            for d in DatasetRepository(session).list_recent(limit=limit)
        ]


def load_saved(dataset_id: str) -> tuple[pd.DataFrame, str]:
    """Load a saved dataset's payload. Returns (frame, name)."""
    if get_settings().demo_mode:
        raise StorageError("no database configured")
    from app.data.db import session_scope
    from app.data.repositories import DatasetRepository

    with session_scope() as session:
        dataset = DatasetRepository(session).get(dataset_id)
        if dataset is None:
            raise DataValidationError(
                f"unknown dataset: {dataset_id}", user_message="That dataset no longer exists."
            )
        if dataset.payload_parquet is None:
            raise StorageError(
                f"dataset {dataset_id} has no payload",
                user_message="This dataset has no stored data to load.",
            )
        frame = pd.read_parquet(io.BytesIO(dataset.payload_parquet))
        return frame, dataset.name


def delete_saved(dataset_id: str) -> bool:
    """Delete a saved dataset and its runs (cascade). Returns False when unknown."""
    if get_settings().demo_mode:
        return False
    from app.data.db import session_scope
    from app.data.repositories import DatasetRepository

    with session_scope() as session:
        deleted = DatasetRepository(session).delete(dataset_id)
    if deleted:
        logger.info("Dataset deleted: %s", dataset_id)
    return deleted
