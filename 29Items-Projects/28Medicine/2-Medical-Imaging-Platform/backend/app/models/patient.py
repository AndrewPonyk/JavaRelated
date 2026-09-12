"""Patient — top of the DICOM information model.

NOTE (HIPAA): rows here are PHI. Access is audited; columns are encrypted at rest
(RDS/KMS). De-identified research copies live in a separate schema/bucket.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, TimestampMixin, UUIDMixin

if TYPE_CHECKING:
    from app.models.study import Study


class Patient(UUIDMixin, TimestampMixin, Base):
    __tablename__ = "patients"

    # DICOM (0010,0020) Patient ID — natural key within an issuer namespace.
    patient_id: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    # DICOM (0010,0010) Patient Name (PN VR). PHI.
    patient_name: Mapped[str | None] = mapped_column(String(256))
    # DICOM (0010,0030) Patient Birth Date. PHI.
    birth_date: Mapped[str | None] = mapped_column(String(8))  # YYYYMMDD
    # DICOM (0010,0040) Patient Sex.
    sex: Mapped[str | None] = mapped_column(String(16))

    studies: Mapped[list[Study]] = relationship(
        back_populates="patient", cascade="all, delete-orphan"
    )
