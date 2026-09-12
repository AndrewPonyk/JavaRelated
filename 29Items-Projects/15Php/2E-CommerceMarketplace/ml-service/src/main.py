"""FastAPI fraud-scoring service.

Exposes:
  - POST /score   : returns a fraud risk in [0, 1] for a transaction's features.
  - GET  /health  : liveness/readiness for Azure Container Apps.

Called by the PHP FraudDetection context's anti-corruption layer (HttpFraudScorer)
with a tight timeout; that caller fails open to manual REVIEW if this service is
slow or unavailable, so this endpoint never sits on the order critical path.
"""

from __future__ import annotations

from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict, Field

from .model import Features, FraudModel

app = FastAPI(title="Marketplace Fraud Service", version="0.1.0")
_model = FraudModel()


class ScoreRequest(BaseModel):
    features: dict = Field(default_factory=dict)


class ScoreResponse(BaseModel):
    # Allow the "model_version" field name (pydantic reserves the "model_" prefix).
    model_config = ConfigDict(protected_namespaces=())

    risk: float = Field(ge=0.0, le=1.0)
    model_version: str = _model.version


@app.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    features = Features.from_payload(request.features)
    return ScoreResponse(risk=_model.score(features))


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
