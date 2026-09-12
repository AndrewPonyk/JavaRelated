import logging
import os
import secrets
from datetime import UTC, datetime
from typing import Annotated

from fastapi import FastAPI, Header, HTTPException, Request, Response
from redis.exceptions import RedisError
from starlette.middleware.trustedhost import TrustedHostMiddleware

from .model_store import ModelMetadata, ModelStore, create_model_store
from .schemas import (
    ForecastRequest,
    ForecastResponse,
    HealthResponse,
    ModelResponse,
    TrainModelRequest,
)

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))

app = FastAPI(
    title="Inventory Forecast Service",
    version="1.0.0",
    description="Checksummed Holt-trend demand forecasting and model registry.",
)
allowed_hosts = [
    host.strip()
    for host in os.getenv(
        "FORECAST_ALLOWED_HOSTS",
        "localhost,127.0.0.1,forecast-service,inventory-forecast,testserver",
    ).split(",")
    if host.strip()
]
app.add_middleware(TrustedHostMiddleware, allowed_hosts=allowed_hosts)
app.state.model_store = create_model_store()


def store(request: Request) -> ModelStore:
    return request.app.state.model_store


def model_response(model: ModelMetadata) -> ModelResponse:
    return ModelResponse(
        version=model.version,
        feature_version=model.feature_version,
        alpha=model.alpha,
        beta=model.beta,
        mae=model.mae,
        residual_stddev=model.residual_stddev,
        trained_at=datetime.fromisoformat(model.trained_at),
        checksum=model.checksum,
    )


def readiness(request: Request, response: Response) -> HealthResponse:
    model_store = store(request)
    redis_ready = model_store.ping()
    model = model_store.active_model()
    if not redis_ready:
        response.status_code = 503
    return HealthResponse(
        status="ok" if redis_ready else "degraded",
        redis="up" if redis_ready else "down",
        model_version=model.version,
        feature_version=model.feature_version,
    )


@app.get("/health", response_model=HealthResponse, responses={503: {"model": HealthResponse}})
@app.get("/health/ready", response_model=HealthResponse, responses={503: {"model": HealthResponse}})
def health(request: Request, response: Response) -> HealthResponse:
    return readiness(request, response)


@app.get("/health/live", response_model=HealthResponse)
def liveness(request: Request) -> HealthResponse:
    model = store(request).active_model()
    return HealthResponse(
        status="ok",
        redis="not_checked",
        model_version=model.version,
        feature_version=model.feature_version,
    )


@app.get("/v1/models/active", response_model=ModelResponse)
def active_model(request: Request) -> ModelResponse:
    return model_response(store(request).active_model())


@app.post("/v1/models/train", response_model=ModelResponse, status_code=201)
def train_model(
    payload: TrainModelRequest,
    request: Request,
    model_admin_token: Annotated[str | None, Header(alias="X-Model-Admin-Token")] = None,
) -> ModelResponse:
    if os.getenv("FORECAST_REQUIRE_ADMIN_TOKEN", "false").lower() == "true":
        expected = os.getenv("MODEL_ADMIN_TOKEN", "")
        if not expected:
            raise HTTPException(status_code=503, detail="Model administration is not configured")
        if model_admin_token is None or not secrets.compare_digest(model_admin_token, expected):
            raise HTTPException(status_code=401, detail="A valid model administration token is required")
    model_store = store(request)
    model = model_store.train(payload.demand_history, payload.feature_version, payload.model_version)
    try:
        model_store.save(model)
    except RedisError as exception:
        raise HTTPException(status_code=503, detail="Model registry is unavailable") from exception
    return model_response(model)


@app.post("/v1/forecasts", response_model=ForecastResponse)
def forecast(payload: ForecastRequest, request: Request) -> ForecastResponse:
    model_store = store(request)
    model = model_store.active_model()
    if payload.feature_version != model.feature_version:
        raise HTTPException(status_code=409, detail="Feature version is incompatible with the active model")
    predictions = model_store.predict(payload.demand_history, payload.horizon_days, model)
    interval = 1.96 * model.residual_stddev
    lower_bound = [round(max(0.0, prediction - interval), 2) for prediction in predictions]
    upper_bound = [round(prediction + interval, 2) for prediction in predictions]
    return ForecastResponse(
        sku=payload.sku,
        predictions=predictions,
        lower_bound=lower_bound,
        upper_bound=upper_bound,
        model_version=model.version,
        feature_version=model.feature_version,
        model_mae=model.mae,
        generated_at=datetime.now(UTC),
    )
