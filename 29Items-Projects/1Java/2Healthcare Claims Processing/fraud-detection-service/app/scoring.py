"""
Fraud scoring engine with heuristic rules.
Can be extended with real ML models (scikit-learn, TensorFlow, etc.)
"""
from datetime import datetime, date
from typing import List, Tuple
from .models import FraudScoreRequest, RiskFactor, ClaimType

# Model version - increment when scoring logic changes
MODEL_VERSION = "1.0.0"

# Thresholds
HIGH_AMOUNT_THRESHOLD = 5000.0
VERY_HIGH_AMOUNT_THRESHOLD = 15000.0
EXTREME_AMOUNT_THRESHOLD = 50000.0

# Known high-risk procedure codes (simplified for demo)
HIGH_RISK_PROCEDURES = {
    "99215": 0.05,  # High-level office visit
    "99223": 0.08,  # High-level hospital admission
    "99285": 0.10,  # Emergency dept high severity
    "27447": 0.07,  # Knee replacement
    "93458": 0.06,  # Cardiac catheterization
}

# Known high-risk diagnosis patterns
HIGH_RISK_DIAGNOSIS_PATTERNS = {
    "M79": 0.08,   # Soft tissue disorders (often vague)
    "R51": 0.05,   # Headache (non-specific)
    "R10": 0.04,   # Abdominal pain
}


class FraudScoringEngine:
    """
    Heuristic-based fraud scoring engine.

    Scoring factors:
    - Claim amount anomalies
    - Service date patterns (weekends, holidays)
    - Provider risk indicators
    - Procedure/diagnosis code analysis
    - Claim velocity patterns
    """

    def __init__(self):
        self.model_version = MODEL_VERSION

    def score(self, request: FraudScoreRequest) -> Tuple[float, List[RiskFactor]]:
        """
        Calculate fraud score for a claim.
        Returns tuple of (score, risk_factors).
        """
        risk_factors: List[RiskFactor] = []
        total_score = 0.0

        # 1. Amount-based scoring
        amount_score, amount_factors = self._score_amount(request.amount)
        total_score += amount_score
        risk_factors.extend(amount_factors)

        # 2. Service date analysis
        date_score, date_factors = self._score_service_date(request.serviceDate)
        total_score += date_score
        risk_factors.extend(date_factors)

        # 3. Procedure code analysis
        proc_score, proc_factors = self._score_procedures(request.procedureCodes)
        total_score += proc_score
        risk_factors.extend(proc_factors)

        # 4. Diagnosis code analysis
        diag_score, diag_factors = self._score_diagnosis(request.diagnosisCodes)
        total_score += diag_score
        risk_factors.extend(diag_factors)

        # 5. Claim type risk
        type_score, type_factors = self._score_claim_type(request.claimType)
        total_score += type_score
        risk_factors.extend(type_factors)

        # 6. Round amount detection
        round_score, round_factors = self._score_round_amounts(request.amount)
        total_score += round_score
        risk_factors.extend(round_factors)

        # Normalize score to 0-1 range
        final_score = min(max(total_score, 0.0), 1.0)

        return final_score, risk_factors

    def _score_amount(self, amount: float) -> Tuple[float, List[RiskFactor]]:
        """Score based on claim amount."""
        factors = []
        score = 0.0

        if amount >= EXTREME_AMOUNT_THRESHOLD:
            score = 0.30
            factors.append(RiskFactor(
                factor="extreme_amount",
                weight=0.30,
                description=f"Extreme claim amount (${amount:,.2f})"
            ))
        elif amount >= VERY_HIGH_AMOUNT_THRESHOLD:
            score = 0.20
            factors.append(RiskFactor(
                factor="very_high_amount",
                weight=0.20,
                description=f"Very high claim amount (${amount:,.2f})"
            ))
        elif amount >= HIGH_AMOUNT_THRESHOLD:
            score = 0.10
            factors.append(RiskFactor(
                factor="high_amount",
                weight=0.10,
                description=f"High claim amount (${amount:,.2f})"
            ))

        return score, factors

    def _score_service_date(self, service_date_str: str) -> Tuple[float, List[RiskFactor]]:
        """Score based on service date patterns."""
        factors = []
        score = 0.0

        try:
            service_date = datetime.strptime(service_date_str, "%Y-%m-%d").date()

            # Weekend services
            if service_date.weekday() >= 5:  # Saturday = 5, Sunday = 6
                score += 0.08
                factors.append(RiskFactor(
                    factor="weekend_service",
                    weight=0.08,
                    description="Service performed on weekend"
                ))

            # Future date (shouldn't happen but flag it)
            if service_date > date.today():
                score += 0.40
                factors.append(RiskFactor(
                    factor="future_service_date",
                    weight=0.40,
                    description="Service date is in the future"
                ))

            # Very old claims (> 90 days)
            days_old = (date.today() - service_date).days
            if days_old > 90:
                score += 0.05
                factors.append(RiskFactor(
                    factor="old_claim",
                    weight=0.05,
                    description=f"Claim is {days_old} days old"
                ))

        except ValueError:
            score += 0.05
            factors.append(RiskFactor(
                factor="invalid_date",
                weight=0.05,
                description="Invalid service date format"
            ))

        return score, factors

    def _score_procedures(self, procedure_codes: str | None) -> Tuple[float, List[RiskFactor]]:
        """Score based on procedure codes."""
        factors = []
        score = 0.0

        if not procedure_codes:
            return score, factors

        codes = [c.strip() for c in procedure_codes.split(",")]

        for code in codes:
            if code in HIGH_RISK_PROCEDURES:
                risk = HIGH_RISK_PROCEDURES[code]
                score += risk
                factors.append(RiskFactor(
                    factor=f"high_risk_procedure_{code}",
                    weight=risk,
                    description=f"High-risk procedure code: {code}"
                ))

        # Multiple procedures flag
        if len(codes) > 5:
            score += 0.10
            factors.append(RiskFactor(
                factor="many_procedures",
                weight=0.10,
                description=f"Unusually high number of procedures ({len(codes)})"
            ))

        return score, factors

    def _score_diagnosis(self, diagnosis_codes: str | None) -> Tuple[float, List[RiskFactor]]:
        """Score based on diagnosis codes."""
        factors = []
        score = 0.0

        if not diagnosis_codes:
            return score, factors

        codes = [c.strip() for c in diagnosis_codes.split(",")]

        for code in codes:
            # Check for high-risk patterns (prefix matching)
            for pattern, risk in HIGH_RISK_DIAGNOSIS_PATTERNS.items():
                if code.startswith(pattern):
                    score += risk
                    factors.append(RiskFactor(
                        factor=f"vague_diagnosis_{code}",
                        weight=risk,
                        description=f"Non-specific diagnosis code: {code}"
                    ))
                    break

        return score, factors

    def _score_claim_type(self, claim_type: ClaimType) -> Tuple[float, List[RiskFactor]]:
        """Score based on claim type."""
        factors = []
        score = 0.0

        # Higher risk claim types
        high_risk_types = {
            ClaimType.DURABLE_MEDICAL_EQUIPMENT: 0.08,
            ClaimType.REHABILITATION: 0.05,
            ClaimType.INPATIENT: 0.05,
        }

        if claim_type in high_risk_types:
            risk = high_risk_types[claim_type]
            score = risk
            factors.append(RiskFactor(
                factor=f"high_risk_claim_type",
                weight=risk,
                description=f"Higher-risk claim type: {claim_type.value}"
            ))

        return score, factors

    def _score_round_amounts(self, amount: float) -> Tuple[float, List[RiskFactor]]:
        """Detect suspiciously round amounts."""
        factors = []
        score = 0.0

        # Check if amount is exactly divisible by 1000
        if amount >= 1000 and amount % 1000 == 0:
            score = 0.08
            factors.append(RiskFactor(
                factor="round_thousands",
                weight=0.08,
                description="Suspiciously round amount (exact thousands)"
            ))
        # Check if amount is exactly divisible by 100
        elif amount >= 100 and amount % 100 == 0:
            score = 0.04
            factors.append(RiskFactor(
                factor="round_hundreds",
                weight=0.04,
                description="Round amount (exact hundreds)"
            ))

        return score, factors

    def get_risk_level(self, score: float) -> str:
        """Convert score to risk level."""
        if score >= 0.7:
            return "CRITICAL"
        elif score >= 0.5:
            return "HIGH"
        elif score >= 0.3:
            return "MEDIUM"
        else:
            return "LOW"

    def get_recommendation(self, score: float) -> str:
        """Get recommended action based on score."""
        if score >= 0.7:
            return "DENY - Refer to Special Investigations Unit"
        elif score >= 0.5:
            return "HOLD - Manual review required"
        elif score >= 0.3:
            return "REVIEW - Flag for secondary review"
        else:
            return "APPROVE - Proceed with normal processing"


# Singleton instance
scoring_engine = FraudScoringEngine()
