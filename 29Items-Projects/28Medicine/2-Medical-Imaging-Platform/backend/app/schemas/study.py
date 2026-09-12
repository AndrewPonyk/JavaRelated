"""Study API schemas."""

from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class StudyBase(BaseModel):
    study_instance_uid: str = Field(..., max_length=64)
    accession_number: str | None = None
    study_date: str | None = Field(None, pattern=r"^\d{8}$")  # YYYYMMDD
    description: str | None = None
    modalities: str | None = None


class StudyRead(StudyBase):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    series_count: int = 0
    created_at: datetime


class StudyQuery(BaseModel):
    """QIDO-RS-style worklist filter."""

    patient_id: str | None = None
    accession_number: str | None = None
    modality: str | None = None
    study_date_from: str | None = Field(None, pattern=r"^\d{8}$")
    study_date_to: str | None = Field(None, pattern=r"^\d{8}$")
    limit: int = Field(50, ge=1, le=200)
    offset: int = Field(0, ge=0)


class PageMeta(BaseModel):
    total: int
    limit: int
    offset: int


class StudyPage(BaseModel):
    items: list[StudyRead]
    meta: PageMeta
