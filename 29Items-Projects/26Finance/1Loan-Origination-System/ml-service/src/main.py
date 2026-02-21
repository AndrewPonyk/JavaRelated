from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import numpy as np
from typing import List, Optional
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Loan Credit Scoring Service",
    description="Rule-based credit risk scoring service. A trained ML model (XGBoost) can be "
                "substituted here once training data is available.",
    version="1.0.0"
)

# Rule-based scoring implementation.
# When a trained model is ready, load it here and replace the rule-based logic in score_applicant().
# Example (do not uncomment until model file exists):
#   import xgboost as xgb
#   model = xgb.Booster()
#   model.load_model('models/credit_scoring_model.json')


class ScoringRequest(BaseModel):
    annual_income: float
    existing_debt: float
    loan_amount: float
    employment_years: float
    age: int
    num_previous_loans: Optional[int] = 0
    num_delinquencies: Optional[int] = 0

class ScoringResponse(BaseModel):
    credit_score: int  # 300-850 range
    risk_score: float  # 0-1 probability of default
    risk_category: str  # LOW, MEDIUM, HIGH
    feature_importance: dict

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "rule-based-scoring"}

@app.post("/api/score", response_model=ScoringResponse)
async def score_applicant(request: ScoringRequest):
    """
    Score an applicant's creditworthiness using rule-based heuristics.
    This is a deterministic fallback scorer — replace with a trained model when available.
    """
    try:
        logger.info(f"Scoring request for income: {request.annual_income}")

        # Calculate debt-to-income ratio
        dti_ratio = request.existing_debt / request.annual_income if request.annual_income > 0 else 1.0

        # TODO: Integration point for future XGBoost model prediction
        base_score = 650

        # Income adjustment
        if request.annual_income >= 100000:
            base_score += 50
        elif request.annual_income >= 75000:
            base_score += 30
        elif request.annual_income < 30000:
            base_score -= 50

        # DTI adjustment
        if dti_ratio > 0.5:
            base_score -= 80
        elif dti_ratio > 0.43:
            base_score -= 50
        elif dti_ratio < 0.3:
            base_score += 30

        # Employment stability adjustment
        if request.employment_years >= 5:
            base_score += 20
        elif request.employment_years < 1:
            base_score -= 30

        # Delinquency penalty
        base_score -= request.num_delinquencies * 30

        # Clamp to valid FICO range
        credit_score = max(300, min(850, base_score))

        # Risk score: inverse of normalized credit score
        risk_score = 1.0 - ((credit_score - 300) / 550)

        # Risk category thresholds
        if risk_score < 0.3:
            risk_category = "LOW"
        elif risk_score < 0.6:
            risk_category = "MEDIUM"
        else:
            risk_category = "HIGH"

        # Static feature weights (heuristic values; replace with SHAP values from trained model)
        feature_importance = {
            "annual_income": 0.25,
            "debt_to_income_ratio": 0.30,
            "employment_years": 0.15,
            "existing_debt": 0.20,
            "num_delinquencies": 0.10
        }

        return ScoringResponse(
            credit_score=credit_score,
            risk_score=round(risk_score, 4),
            risk_category=risk_category,
            feature_importance=feature_importance
        )

    except Exception as e:
        logger.error(f"Error scoring applicant: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Scoring error: {str(e)}")

@app.post("/api/score/batch", response_model=List[ScoringResponse])
async def score_batch(requests: List[ScoringRequest]):
    """
    Batch scoring for multiple applicants.
    """
    results = []
    for req in requests:
        result = await score_applicant(req)
        results.append(result)
    return results

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
