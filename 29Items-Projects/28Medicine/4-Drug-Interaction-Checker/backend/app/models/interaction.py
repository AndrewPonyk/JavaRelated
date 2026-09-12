"""Interaction request/response schemas."""

from pydantic import BaseModel, Field

from app.models.common import EvidenceLevel, Severity
from app.models.drug import DrugInput


class InteractionPair(BaseModel):
    rxcui_a: str
    rxcui_b: str
    name_a: str
    name_b: str
    severity: Severity
    evidence_level: EvidenceLevel | None = None
    mechanism: str | None = None
    description: str | None = None
    source: str | None = None
    ml_predicted: bool = False
    ml_confidence: float | None = Field(default=None, ge=0.0, le=1.0)


class InteractionCheckRequest(BaseModel):
    drugs: list[DrugInput] = Field(..., min_length=1)
    include_ml_prediction: bool = True


class InteractionCheckResponse(BaseModel):
    checked_drugs: list[str]
    unresolved: list[str] = Field(
        default_factory=list, description="Inputs that could not be normalized"
    )
    interactions: list[InteractionPair]
    highest_severity: Severity


# --------------------------------------------------------------------------- #
# Admin / ingestion schemas
# --------------------------------------------------------------------------- #
class InteractionUpsert(BaseModel):
    rxcui_a: str = Field(..., min_length=1)
    rxcui_b: str = Field(..., min_length=1)
    severity: Severity
    mechanism: str | None = None
    evidence_level: EvidenceLevel | None = None
    description: str | None = None
    source: str | None = "manual"


class InteractionListResponse(BaseModel):
    items: list[InteractionPair]
    total: int
    limit: int
    offset: int
