"""
Pydantic models for fraud detection API.
"""
from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import date
from enum import Enum


class ClaimType(str, Enum):
    MEDICAL = "MEDICAL"
    DENTAL = "DENTAL"
    VISION = "VISION"
    PHARMACY = "PHARMACY"
    MENTAL_HEALTH = "MENTAL_HEALTH"
    REHABILITATION = "REHABILITATION"
    DURABLE_MEDICAL_EQUIPMENT = "DURABLE_MEDICAL_EQUIPMENT"
    LABORATORY = "LABORATORY"
    RADIOLOGY = "RADIOLOGY"
    EMERGENCY = "EMERGENCY"
    INPATIENT = "INPATIENT"
    OUTPATIENT = "OUTPATIENT"


class FraudScoreRequest(BaseModel):
    """Request model for fraud scoring."""
    claimId: str = Field(..., description="Unique claim identifier")
    claimType: ClaimType = Field(..., description="Type of claim")
    amount: float = Field(..., gt=0, description="Claim amount in dollars")
    serviceDate: str = Field(..., description="Date of service (YYYY-MM-DD)")
    patientId: str = Field(..., description="Patient identifier")
    providerId: str = Field(..., description="Provider identifier")
    diagnosisCodes: Optional[str] = Field(None, description="ICD-10 diagnosis codes")
    procedureCodes: Optional[str] = Field(None, description="CPT procedure codes")

    class Config:
        json_schema_extra = {
            "example": {
                "claimId": "550e8400-e29b-41d4-a716-446655440000",
                "claimType": "MEDICAL",
                "amount": 1500.00,
                "serviceDate": "2024-01-15",
                "patientId": "patient-123",
                "providerId": "provider-456",
                "diagnosisCodes": "J06.9",
                "procedureCodes": "99213"
            }
        }


class RiskFactor(BaseModel):
    """Individual risk factor identified."""
    factor: str = Field(..., description="Name of the risk factor")
    weight: float = Field(..., description="Weight/contribution to score")
    description: str = Field(..., description="Human-readable description")


class FraudScoreResponse(BaseModel):
    """Response model for fraud scoring."""
    claimId: str = Field(..., description="Claim identifier from request")
    score: float = Field(..., ge=0, le=1, description="Fraud probability score (0-1)")
    riskLevel: str = Field(..., description="Risk level: LOW, MEDIUM, HIGH, CRITICAL")
    riskFactors: List[RiskFactor] = Field(default=[], description="Identified risk factors")
    recommendation: str = Field(..., description="Recommended action")
    modelVersion: str = Field(default="1.0.0", description="Scoring model version")

    class Config:
        json_schema_extra = {
            "example": {
                "claimId": "550e8400-e29b-41d4-a716-446655440000",
                "score": 0.35,
                "riskLevel": "MEDIUM",
                "riskFactors": [
                    {
                        "factor": "high_amount",
                        "weight": 0.15,
                        "description": "Claim amount above typical threshold"
                    }
                ],
                "recommendation": "REVIEW",
                "modelVersion": "1.0.0"
            }
        }


class HealthResponse(BaseModel):
    """Health check response."""
    status: str = Field(..., description="Service status")
    version: str = Field(..., description="API version")
    model_loaded: bool = Field(..., description="Whether scoring model is ready")
