"""Standalone ML severity-prediction microservice.

Shares the codebase with the API but runs as its own EKS Deployment so it can
scale and be resourced independently. Run: uvicorn app.ml_main:app
"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging, get_logger
from app.db.neo4j_client import close_driver, init_driver
from app.ml.model import SeverityModel
from app.ml.predict import predict_severity, predict_severity_batch

logger = get_logger(__name__)


class PredictRequest(BaseModel):
    rxcui_a: str
    rxcui_b: str


class PredictBatchRequest(BaseModel):
    pairs: list[PredictRequest] = Field(..., min_length=1)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    configure_logging()
    await init_driver()
    model = SeverityModel.instance()  # warm the in-memory model cache
    logger.info("ml_service_started", model_mode=model.mode)
    yield
    await close_driver()


app = FastAPI(title="DIC ML Severity Service", lifespan=lifespan)
register_exception_handlers(app)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "model_mode": SeverityModel.instance().mode}


@app.post("/predict")
async def predict(req: PredictRequest) -> dict:
    return await predict_severity(req.rxcui_a, req.rxcui_b)


@app.post("/predict/batch")
async def predict_batch(req: PredictBatchRequest) -> dict:
    pairs = [(p.rxcui_a, p.rxcui_b) for p in req.pairs]
    return {"predictions": await predict_severity_batch(pairs)}
