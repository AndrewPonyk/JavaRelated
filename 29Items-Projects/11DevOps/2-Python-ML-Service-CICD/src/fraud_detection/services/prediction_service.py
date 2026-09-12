"""Fraud scoring orchestration: routing, inference, metrics, persistence.

Persistence policy: recording the prediction (and the sticky A/B
assignment) is best-effort. A database outage or a duplicate transaction
id must never fail the scoring path - failures are rolled back, logged
and counted via ``fraud_db_errors_total`` while the caller still receives
the score.
"""

from __future__ import annotations

import time
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any, cast

from sqlalchemy.orm import Session

from fraud_detection.core.logging import get_logger
from fraud_detection.db.models import Prediction
from fraud_detection.db.repositories import PredictionRepository
from fraud_detection.ml.features import build_feature_vector
from fraud_detection.monitoring.metrics import (
    FRAUD_DB_ERRORS_TOTAL,
    FRAUD_PREDICTION_LATENCY_SECONDS,
    FRAUD_PREDICTIONS_TOTAL,
)
from fraud_detection.schemas.prediction import PredictionRequest, PredictionResponse
from fraud_detection.services.ab_router import ABRouter, Variant
from fraud_detection.services.model_loader import ModelLoader, ScoringModel

logger = get_logger(__name__)

FRAUD_THRESHOLD = 0.5


class PredictionService:
    """Scores transactions using the A/B-selected model variant."""

    def __init__(self, ab_router: ABRouter, model_loader: ModelLoader) -> None:
        self._ab_router = ab_router
        self._model_loader = model_loader

    def predict(
        self, request: PredictionRequest, session: Session | None = None
    ) -> PredictionResponse:
        """Score one transaction.

        Args:
            request: Validated prediction request.
            session: Optional DB session for audit persistence and sticky
                A/B assignments; scoring works without one.

        Returns:
            The fraud score plus routing/latency metadata.
        """
        variant = self._ab_router.assign_variant(request.account_id, session)
        model = self._model_loader.get_model(alias=variant)

        features = build_feature_vector(
            request.amount, request.merchant_category, request.timestamp, request.features
        )
        scoring_input: dict[str, Any] = {
            **features,
            "amount": float(request.amount),
            "merchant_category": request.merchant_category,
        }

        start = time.perf_counter()
        probability = float(min(max(model.predict_proba(scoring_input), 0.0), 1.0))
        elapsed_seconds = time.perf_counter() - start

        is_fraud = probability >= FRAUD_THRESHOLD
        outcome = "fraud" if is_fraud else "legit"
        FRAUD_PREDICTION_LATENCY_SECONDS.observe(elapsed_seconds)
        FRAUD_PREDICTIONS_TOTAL.labels(
            variant=variant, model_version=model.version, outcome=outcome
        ).inc()

        response = PredictionResponse(
            transaction_id=request.transaction_id,
            model_version=model.version,
            variant=variant,
            fraud_probability=probability,
            is_fraud=is_fraud,
            latency_ms=elapsed_seconds * 1000.0,
        )

        if session is not None:
            self._persist(session, request, response, features, model)

        return response

    def _persist(
        self,
        session: Session,
        request: PredictionRequest,
        response: PredictionResponse,
        features: dict[str, float],
        model: ScoringModel,
    ) -> None:
        """Best-effort audit persistence (prediction row + A/B assignment).

        Each write runs inside its own SAVEPOINT so a failure (e.g. a
        duplicate ``transaction_id``) rolls back only that write and never
        discards a sibling row already flushed on this session.
        """
        try:
            with session.begin_nested():
                PredictionRepository(session).add(
                    Prediction(
                        transaction_id=request.transaction_id,
                        account_id=request.account_id,
                        amount=Decimal(str(request.amount)),
                        model_version=model.version,
                        variant=response.variant,
                        fraud_probability=response.fraud_probability,
                        is_fraud=response.is_fraud,
                        latency_ms=response.latency_ms,
                        features=features,
                        created_at=datetime.now(UTC),
                    )
                )
        except Exception:  # noqa: BLE001 - policy: scoring never fails on the audit trail
            FRAUD_DB_ERRORS_TOTAL.inc()
            logger.warning("prediction_persist_failed", transaction_id=request.transaction_id)
            return
        self._ab_router.record_assignment(
            request.account_id, cast(Variant, response.variant), model.version, session
        )
