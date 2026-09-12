"""Instance — a single DICOM object (one image / SOP instance).

The pixel data itself lives in object storage; this row holds metadata and the
object key so we never load PixelData into Postgres.
"""

from __future__ import annotations

import uuid
from typing import TYPE_CHECKING

from sqlalchemy import ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import GUID, Base, TimestampMixin, UUIDMixin

if TYPE_CHECKING:
    from app.models.ml_result import MlResult
    from app.models.series import Series


class Instance(UUIDMixin, TimestampMixin, Base):
    __tablename__ = "instances"

    # DICOM (0008,0018) SOP Instance UID — idempotency key for ingestion.
    sop_instance_uid: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    # DICOM (0008,0016) SOP Class UID.
    sop_class_uid: Mapped[str | None] = mapped_column(String(64))
    # DICOM (0020,0013) Instance Number.
    instance_number: Mapped[int | None] = mapped_column(Integer)
    # DICOM (0002,0010) Transfer Syntax UID — needed to decode pixels.
    transfer_syntax_uid: Mapped[str | None] = mapped_column(String(64))
    rows: Mapped[int | None] = mapped_column(Integer)  # (0028,0010)
    columns: Mapped[int | None] = mapped_column(Integer)  # (0028,0011)

    # Object-store coordinates of the canonical Part-10 file.
    object_key: Mapped[str] = mapped_column(String(512))
    size_bytes: Mapped[int | None] = mapped_column(Integer)

    series_pk: Mapped[uuid.UUID] = mapped_column(
        GUID, ForeignKey("series.id", ondelete="CASCADE"), index=True
    )
    series: Mapped[Series] = relationship(back_populates="instances")
    ml_results: Mapped[list[MlResult]] = relationship(
        back_populates="instance", cascade="all, delete-orphan"
    )
