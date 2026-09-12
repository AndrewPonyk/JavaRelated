"""Equation-pattern recognition. SageMaker-backed, heuristic fallback —
this endpoint is an *enrichment* and must never fail hard when ML is down
(docs/ARCHITECTURE.md §2.6, fail-soft principle).
"""

from fastapi import APIRouter

from app.schemas.computation import ClassifyRequest, ClassifyResponse
from app.services import sagemaker_client

router = APIRouter()


@router.post("/classify", response_model=ClassifyResponse)
def classify_equation(payload: ClassifyRequest) -> ClassifyResponse:
    prediction = sagemaker_client.classify_equation(payload.expression)
    return ClassifyResponse(
        label=prediction.label,
        confidence=prediction.confidence,
        source=prediction.source,  # "sagemaker" or "heuristic" — the truth field
    )
