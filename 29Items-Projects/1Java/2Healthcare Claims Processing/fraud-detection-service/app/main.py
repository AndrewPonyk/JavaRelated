"""
Fraud Detection API - FastAPI Service
ML-powered fraud scoring for healthcare claims.
"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import logging
from datetime import datetime

from .models import (
    FraudScoreRequest,
    FraudScoreResponse,
    HealthResponse,
)
from .scoring import scoring_engine

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# API metadata
API_VERSION = "1.0.0"
API_TITLE = "Fraud Detection API"
API_DESCRIPTION = """
ML-powered fraud detection service for healthcare claims processing.

## Features
- Real-time fraud scoring
- Risk factor analysis
- Configurable thresholds
- Health monitoring

## Scoring Model
The current model uses heuristic rules including:
- Amount anomaly detection
- Service date pattern analysis
- Procedure/diagnosis code risk assessment
- Claim velocity monitoring
"""

# Create FastAPI app
app = FastAPI(
    title=API_TITLE,
    description=API_DESCRIPTION,
    version=API_VERSION,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Request counter for metrics
request_count = 0
start_time = datetime.now()


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    """
    Health check endpoint.
    Returns service status and model readiness.
    """
    return HealthResponse(
        status="UP",
        version=API_VERSION,
        model_loaded=True
    )


@app.get("/", tags=["Health"])
async def root():
    """Root endpoint with API info."""
    return {
        "service": API_TITLE,
        "version": API_VERSION,
        "status": "running",
        "docs": "/docs"
    }


@app.post(
    "/api/v1/score",
    response_model=FraudScoreResponse,
    tags=["Scoring"],
    summary="Score a claim for fraud",
    description="Analyzes a healthcare claim and returns a fraud probability score with risk factors."
)
async def score_claim(request: FraudScoreRequest):
    """
    Score a claim for potential fraud.

    The scoring engine analyzes multiple factors:
    - Claim amount patterns
    - Service date anomalies
    - Procedure/diagnosis code risks
    - Claim type risk profiles

    Returns a score between 0 and 1, where:
    - 0.0-0.3: LOW risk
    - 0.3-0.5: MEDIUM risk
    - 0.5-0.7: HIGH risk
    - 0.7-1.0: CRITICAL risk
    """
    global request_count
    request_count += 1

    logger.info(f"Scoring claim: {request.claimId}, amount: ${request.amount:,.2f}")

    try:
        # Calculate fraud score
        score, risk_factors = scoring_engine.score(request)
        risk_level = scoring_engine.get_risk_level(score)
        recommendation = scoring_engine.get_recommendation(score)

        logger.info(
            f"Claim {request.claimId} scored: {score:.2f} ({risk_level}), "
            f"factors: {len(risk_factors)}"
        )

        return FraudScoreResponse(
            claimId=request.claimId,
            score=round(score, 4),
            riskLevel=risk_level,
            riskFactors=risk_factors,
            recommendation=recommendation,
            modelVersion=scoring_engine.model_version
        )

    except Exception as e:
        logger.error(f"Scoring error for claim {request.claimId}: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Scoring failed: {str(e)}"
        )


@app.get("/api/v1/metrics", tags=["Monitoring"])
async def get_metrics():
    """
    Get service metrics.
    """
    uptime = (datetime.now() - start_time).total_seconds()
    return {
        "requests_total": request_count,
        "uptime_seconds": uptime,
        "model_version": scoring_engine.model_version,
        "avg_requests_per_minute": (request_count / uptime * 60) if uptime > 0 else 0
    }


@app.get("/api/v1/model/info", tags=["Model"])
async def get_model_info():
    """
    Get information about the scoring model.
    """
    return {
        "version": scoring_engine.model_version,
        "type": "heuristic_rules",
        "description": "Rule-based fraud detection with weighted scoring",
        "thresholds": {
            "low": "0.0 - 0.3",
            "medium": "0.3 - 0.5",
            "high": "0.5 - 0.7",
            "critical": "0.7 - 1.0"
        },
        "factors": [
            "amount_analysis",
            "service_date_patterns",
            "procedure_code_risk",
            "diagnosis_code_risk",
            "claim_type_risk",
            "round_amount_detection"
        ]
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8090)
