from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel
import numpy as np
import pandas as pd
import xgboost as xgb
from typing import List, Optional
import logging
import os

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Loan Credit Scoring Service",
    description="Credit scoring with two modes: XGBoost model (German dataset) and rule-based. "
                "Switch via ?mode=model or ?mode=rules",
    version="2.0.0"
)

# ---------------------------------------------------------------------------
# XGBoost model loading
# ---------------------------------------------------------------------------
MODEL_PATH = os.path.join(os.path.dirname(__file__), "..", "models", "loan_model.json")

xgb_model: Optional[xgb.XGBClassifier] = None
try:
    xgb_model = xgb.XGBClassifier()
    xgb_model.load_model(MODEL_PATH)
    logger.info(f"XGBoost model loaded from {MODEL_PATH}")
except Exception as e:
    logger.error(f"Failed to load XGBoost model: {e}. Only rule-based scoring available.")
    xgb_model = None

FEATURE_COLUMNS = [
    "annualIncome", "existingDebt", "loanAmount",
    "employmentYears", "age", "numPreviousLoans", "numDelinquencies",
]

# Default scoring mode: "model" if loaded, else "rules"
DEFAULT_MODE = "model" if xgb_model else "rules"


# ---------------------------------------------------------------------------
# Preprocessing: raw app values -> German dataset categories
# German Credit Dataset encoding reference (from train_app_model.py):
#   annualIncome  (idx 0):  1:<0 DM, 2:0-200, 3:>=200/Salary, 4:No account
#   existingDebt  (idx 10): 1:Banks, 2:Stores, 3:None
#   loanAmount    (idx 3):  numeric (pass-through)
#   employmentYrs (idx 5):  1:0yr, 2:<1yr, 3:1-4yr, 4:4-7yr, 5:>7yr
#   age           (idx 9):  numeric (pass-through)
#   numPrevLoans  (idx 12): numeric (pass-through)
#   numDelinq     (idx 2):  0,1,2:None, 3:Had delays, 4:Critical
# ---------------------------------------------------------------------------
def encode_annual_income(income_usd: float) -> int:
    if income_usd < 0:
        return 1
    elif income_usd < 20000:
        return 2
    elif income_usd < 50000:
        return 3
    else:
        return 4


def encode_existing_debt(debt_usd: float) -> int:
    if debt_usd > 50000:
        return 1  # heavy debt -> banks
    elif debt_usd > 0:
        return 2  # some debt -> stores
    else:
        return 3  # no debt


def encode_employment_years(years: float) -> int:
    if years <= 0:
        return 1
    elif years < 1:
        return 2
    elif years < 4:
        return 3
    elif years < 7:
        return 4
    else:
        return 5


def encode_delinquencies(count: int) -> int:
    if count <= 0:
        return 2  # clean
    elif count <= 2:
        return 3  # had delays
    else:
        return 4  # critical


def preprocess(request: "ScoringRequest") -> pd.DataFrame:
    """Convert raw application values to the feature vector the model expects."""
    row = {
        "annualIncome": encode_annual_income(request.annual_income),
        "existingDebt": encode_existing_debt(request.existing_debt),
        "loanAmount": request.loan_amount,
        "employmentYears": encode_employment_years(request.employment_years),
        "age": request.age,
        "numPreviousLoans": request.num_previous_loans or 0,
        "numDelinquencies": encode_delinquencies(request.num_delinquencies or 0),
    }
    return pd.DataFrame([row], columns=FEATURE_COLUMNS)


# ---------------------------------------------------------------------------
# Hard business rules (things the model cannot distinguish)
# Model sees categories, not absolute dollar amounts, so it can't tell
# 100k income from 1M income. These rules enforce real-world caps.
# ---------------------------------------------------------------------------
def apply_hard_rules(request: "ScoringRequest") -> Optional[str]:
    """Return a rejection reason if hard business rules are violated, else None."""
    # Max loan = 70% of projected 10-year income
    max_loan = request.annual_income * 10 * 0.7
    if request.loan_amount > max_loan:
        return (
            f"Loan amount ${request.loan_amount:,.0f} exceeds maximum "
            f"${max_loan:,.0f} (70% of 10-year projected income)"
        )
    return None


# ---------------------------------------------------------------------------
# Request / Response models
# ---------------------------------------------------------------------------
class ScoringRequest(BaseModel):
    annual_income: float
    existing_debt: float
    loan_amount: float
    employment_years: float
    age: int
    num_previous_loans: Optional[int] = 0
    num_delinquencies: Optional[int] = 0


class ScoringResponse(BaseModel):
    credit_score: int           # 300-850 range
    risk_score: float           # 0-1 probability of default
    risk_category: str          # LOW, MEDIUM, HIGH, HARD_REJECT
    feature_importance: dict
    scoring_mode: str           # "model" or "rules"
    hard_rule_violation: Optional[str] = None


# ---------------------------------------------------------------------------
# Scoring: XGBoost model
# ---------------------------------------------------------------------------
def score_with_model(request: ScoringRequest) -> ScoringResponse:
    features_df = preprocess(request)
    prob_default = float(xgb_model.predict_proba(features_df)[0][1])

    credit_score = int(300 + (1.0 - prob_default) * 550)
    credit_score = max(300, min(850, credit_score))

    risk_score = round(prob_default, 4)
    risk_category = "LOW" if risk_score < 0.3 else "MEDIUM" if risk_score < 0.6 else "HIGH"

    importances = dict(zip(FEATURE_COLUMNS, xgb_model.feature_importances_.tolist()))

    return ScoringResponse(
        credit_score=credit_score,
        risk_score=risk_score,
        risk_category=risk_category,
        feature_importance=importances,
        scoring_mode="model",
    )


# ---------------------------------------------------------------------------
# Scoring: original rule-based (kept as-is)
# ---------------------------------------------------------------------------
def score_with_rules(request: ScoringRequest) -> ScoringResponse:
    dti_ratio = request.existing_debt / request.annual_income if request.annual_income > 0 else 1.0

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
    base_score -= (request.num_delinquencies or 0) * 30

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

    # Static feature weights
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
        feature_importance=feature_importance,
        scoring_mode="rules",
    )


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "model_loaded": xgb_model is not None,
        "default_mode": DEFAULT_MODE,
    }


@app.post("/api/score", response_model=ScoringResponse)
async def score_applicant(
    request: ScoringRequest,
    mode: str = Query(default=DEFAULT_MODE, regex="^(model|rules)$"),
):
    """
    Score an applicant. Use ?mode=model for XGBoost, ?mode=rules for rule-based.
    Hard business rules (loan cap) are always applied regardless of mode.
    """
    try:
        logger.info(f"Scoring request [mode={mode}]: income={request.annual_income}, loan={request.loan_amount}")

        # Hard business rules always run first (both modes)
        violation = apply_hard_rules(request)
        if violation:
            return ScoringResponse(
                credit_score=300,
                risk_score=1.0,
                risk_category="HARD_REJECT",
                feature_importance={},
                scoring_mode=mode,
                hard_rule_violation=violation,
            )

        if mode == "model":
            if xgb_model is None:
                logger.warn("Model requested but not loaded, falling back to rules")
                return score_with_rules(request)
            return score_with_model(request)
        else:
            return score_with_rules(request)

    except Exception as e:
        logger.error(f"Error scoring applicant: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Scoring error: {str(e)}")


@app.post("/api/score/batch", response_model=List[ScoringResponse])
async def score_batch(
    requests: List[ScoringRequest],
    mode: str = Query(default=DEFAULT_MODE, regex="^(model|rules)$"),
):
    """Batch scoring for multiple applicants."""
    results = []
    for req in requests:
        result = await score_applicant(req, mode=mode)
        results.append(result)
    return results


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
