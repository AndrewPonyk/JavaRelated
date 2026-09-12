"""
ShopFlow ML service (FastAPI).

Exposes a synchronous sentiment-scoring endpoint and — when enabled — runs a
Kafka pipeline over ``review.events`` → ``review.scored`` (see pipeline.py).
"""
from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel, Field

from .pipeline import ReviewSentimentPipeline
from .sentiment import SentimentResult, analyzer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

_KAFKA_ENABLED = os.getenv("ML_KAFKA_ENABLED", "true").lower() == "true"
_pipeline = ReviewSentimentPipeline()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Start/stop the Kafka pipeline around the app lifecycle."""
    if _KAFKA_ENABLED:
        try:
            await _pipeline.start()
        except Exception as exc:  # degrade to HTTP-only if the broker is down
            logger.warning("Kafka pipeline not started (%s); HTTP scoring still available.", exc)
    yield
    if _KAFKA_ENABLED:
        await _pipeline.stop()


app = FastAPI(
    title="ShopFlow ML Service",
    version="1.0.0",
    description="Sentiment analysis for product reviews.",
    lifespan=lifespan,
)


class ScoreRequest(BaseModel):
    """Inbound text to score."""

    review_id: str = Field(..., examples=["rev-123"])
    text: str = Field(..., min_length=1, max_length=5000)


class ScoreResponse(BaseModel):
    """Sentiment result returned to the caller."""

    review_id: str
    label: str
    score: float


@app.get("/health", tags=["ops"])
def health() -> dict[str, str]:
    """Liveness/readiness probe target."""
    return {"status": "UP"}


@app.post("/api/v1/sentiment", response_model=ScoreResponse, tags=["sentiment"])
def score(request: ScoreRequest) -> ScoreResponse:
    """Score a single review's sentiment."""
    result: SentimentResult = analyzer.analyze(request.text)
    return ScoreResponse(review_id=request.review_id, label=result.label, score=result.score)
