"""SageMaker inference client with a mandatory local fallback.

Contract: this function NEVER raises because ML is down — classification is an
enrichment, not a dependency (docs/ARCHITECTURE.md §2.6). The ``source`` field
tells the client which path answered.
"""

from __future__ import annotations

import json
import logging

from app.core.config import get_settings
from sciengine.ml.features import PatternLabel, PatternPrediction, classify_pattern

logger = logging.getLogger(__name__)

_INVOKE_TIMEOUT_SECONDS = 2.0


def classify_equation(expression: str) -> PatternPrediction:
    settings = get_settings()
    if not settings.sagemaker_recognizer_endpoint:
        return classify_pattern(expression)  # heuristic path (local/dev default)

    try:
        return _invoke_sagemaker(expression)
    except Exception:
        # Phase 3 roadmap (docs/PROJECT-PLAN.md): a circuit breaker skips the
        # endpoint for a cooldown after repeated failures instead of paying
        # the timeout on every request.
        logger.warning("SageMaker classify failed; falling back to heuristic", exc_info=True)
        return classify_pattern(expression)


def _invoke_sagemaker(expression: str) -> PatternPrediction:
    import boto3  # lazy: keeps import-time free of AWS requirements
    from botocore.config import Config

    settings = get_settings()
    client = boto3.client(
        "sagemaker-runtime",
        region_name=settings.aws_region,
        config=Config(
            connect_timeout=_INVOKE_TIMEOUT_SECONDS,
            read_timeout=_INVOKE_TIMEOUT_SECONDS,
            retries={"max_attempts": 1},
        ),
    )
    # Features are computed with the same sciengine code the model trained on
    # (training/serving skew guard — docs/TECH-NOTES.md §3.6).
    from sciengine.ml.features import expression_features

    response = client.invoke_endpoint(
        EndpointName=settings.sagemaker_recognizer_endpoint,
        ContentType="application/json",
        Body=json.dumps({"features": expression_features(expression)}),
    )
    body = json.loads(response["Body"].read())
    return PatternPrediction(
        label=PatternLabel(body["label"]),
        confidence=float(body["confidence"]),
        source="sagemaker",
    )
