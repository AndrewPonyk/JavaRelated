"""Study — a single imaging exam for a patient (DICOM Study)."""

from __future__ import annotations

import uuid
from typing import TYPE_CHECKING

from sqlalchemy import ForeignKey, Index, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import GUID, Base, TimestampMixin, UUIDMixin

if TYPE_CHECKING:
    from app.models.patient import Patient
    from app.models.series import Series


class Study(UUIDMixin, TimestampMixin, Base):
    __tablename__ = "studies"
    __table_args__ = (
        # Worklist is queried by patient + date; index the hot path.
        Index("ix_studies_patient_date", "patient_pk", "study_date"),
    )

    # DICOM (0020,000D) Study Instance UID — globally unique natural key.
    study_instance_uid: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    # DICOM (0008,0050) Accession Number (PHI-adjacent; from RIS/order).
    accession_number: Mapped[str | None] = mapped_column(String(64), index=True)
    # DICOM (0008,0020) Study Date / (0008,0030) Study Time.
    study_date: Mapped[str | None] = mapped_column(String(8))  # YYYYMMDD
    study_time: Mapped[str | None] = mapped_column(String(16))
    # DICOM (0008,1030) Study Description.
    description: Mapped[str | None] = mapped_column(String(256))
    # DICOM (0008,0061) Modalities in Study (e.g. "CR\\DX").
    modalities: Mapped[str | None] = mapped_column(String(64))

    patient_pk: Mapped[uuid.UUID] = mapped_column(
        GUID, ForeignKey("patients.id", ondelete="CASCADE"), index=True
    )
    patient: Mapped[Patient] = relationship(back_populates="studies")
    series: Mapped[list[Series]] = relationship(
        back_populates="study", cascade="all, delete-orphan"
    )
