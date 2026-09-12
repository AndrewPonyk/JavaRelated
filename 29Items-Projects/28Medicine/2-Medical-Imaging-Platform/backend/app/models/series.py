"""Series — a set of related instances within a study (one acquisition)."""

from __future__ import annotations

import uuid
from typing import TYPE_CHECKING

from sqlalchemy import ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import GUID, Base, TimestampMixin, UUIDMixin

if TYPE_CHECKING:
    from app.models.instance import Instance
    from app.models.study import Study


class Series(UUIDMixin, TimestampMixin, Base):
    __tablename__ = "series"

    # DICOM (0020,000E) Series Instance UID.
    series_instance_uid: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    # DICOM (0008,0060) Modality — CR/DX/CT/MR/US... Drives ML routing.
    modality: Mapped[str | None] = mapped_column(String(16), index=True)
    # DICOM (0020,0011) Series Number.
    series_number: Mapped[int | None] = mapped_column(Integer)
    # DICOM (0008,103E) Series Description.
    description: Mapped[str | None] = mapped_column(String(256))
    # DICOM (0018,0015) Body Part Examined.
    body_part: Mapped[str | None] = mapped_column(String(64))

    study_pk: Mapped[uuid.UUID] = mapped_column(
        GUID, ForeignKey("studies.id", ondelete="CASCADE"), index=True
    )
    study: Mapped[Study] = relationship(back_populates="series")
    instances: Mapped[list[Instance]] = relationship(
        back_populates="series", cascade="all, delete-orphan"
    )
