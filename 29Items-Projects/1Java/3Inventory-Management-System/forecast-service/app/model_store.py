import hashlib
import json
import logging
import math
import os
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from statistics import fmean, pstdev
from typing import Any

from redis import Redis
from redis.exceptions import RedisError

LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class ModelMetadata:
    version: str
    feature_version: str
    alpha: float
    beta: float
    mae: float
    residual_stddev: float
    trained_at: str
    checksum: str

    @classmethod
    def create(
        cls,
        version: str,
        feature_version: str,
        alpha: float,
        beta: float,
        mae: float,
        residual_stddev: float,
        trained_at: str | None = None,
    ) -> "ModelMetadata":
        values: dict[str, Any] = {
            "version": version,
            "feature_version": feature_version,
            "alpha": round(alpha, 6),
            "beta": round(beta, 6),
            "mae": round(mae, 6),
            "residual_stddev": round(residual_stddev, 6),
            "trained_at": trained_at or datetime.now(UTC).isoformat(),
        }
        _validate_model_fields(values)
        checksum = _checksum(values)
        return cls(**values, checksum=checksum)

    @classmethod
    def from_json(cls, raw: str) -> "ModelMetadata":
        payload = json.loads(raw)
        checksum = str(payload.pop("checksum"))
        if not hashlib.sha256(_canonical(payload)).hexdigest() == checksum:
            raise ValueError("model checksum does not match metadata")
        model = cls(**payload, checksum=checksum)
        _validate_model_fields(payload)
        return model

    def to_json(self) -> str:
        return json.dumps(asdict(self), sort_keys=True, separators=(",", ":"))


def _canonical(payload: dict[str, Any]) -> bytes:
    return json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _checksum(payload: dict[str, Any]) -> str:
    return hashlib.sha256(_canonical(payload)).hexdigest()


class ModelStore:
    """Versioned, checksummed JSON model registry with a last-known-good in-process copy."""

    def __init__(self, redis_url: str, model_key: str, redis_client: Redis | None = None) -> None:
        self._redis = redis_client or Redis.from_url(
            redis_url, decode_responses=True, socket_connect_timeout=1.0, socket_timeout=1.0
        )
        self._model_key = model_key
        self._active = ModelMetadata.create(
            version="holt-default-v1",
            feature_version="daily-demand-v1",
            alpha=0.5,
            beta=0.1,
            mae=0.0,
            residual_stddev=0.0,
            trained_at="2026-01-01T00:00:00+00:00",
        )

    def ping(self) -> bool:
        try:
            return bool(self._redis.ping())
        except RedisError:
            return False

    def active_model(self) -> ModelMetadata:
        try:
            raw_model = self._redis.get(self._model_key)
        except RedisError as exception:
            LOGGER.warning("Redis unavailable; serving last-known-good model: %s", exception)
            return self._active
        if raw_model is None:
            return self._active
        try:
            candidate = ModelMetadata.from_json(raw_model)
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exception:
            LOGGER.error("Rejected invalid model metadata; retaining last-known-good model: %s", exception)
            return self._active
        self._active = candidate
        return candidate

    def save(self, model: ModelMetadata) -> None:
        ModelMetadata.from_json(model.to_json())
        self._redis.set(self._model_key, model.to_json())
        self._active = model

    @staticmethod
    def predict(history: list[float], horizon_days: int, model: ModelMetadata) -> list[float]:
        level, trend = _fit(history, model.alpha, model.beta)
        return [round(max(0.0, level + trend * step), 2) for step in range(1, horizon_days + 1)]

    @staticmethod
    def train(
        history: list[float], feature_version: str, requested_version: str | None
    ) -> ModelMetadata:
        candidates = [(alpha, beta) for alpha in (0.2, 0.5, 0.8) for beta in (0.05, 0.2, 0.4)]
        best_alpha, best_beta = candidates[0]
        best_errors: list[float] = []
        best_mae = float("inf")
        start = max(5, len(history) // 3)
        for alpha, beta in candidates:
            errors = _walk_forward_errors(history, start, alpha, beta)
            mae = fmean(abs(error) for error in errors)
            if mae < best_mae:
                best_alpha, best_beta, best_errors, best_mae = alpha, beta, errors, mae
        version = requested_version or f"holt-{datetime.now(UTC).strftime('%Y%m%d%H%M%S')}"
        return ModelMetadata.create(
            version=version,
            feature_version=feature_version,
            alpha=best_alpha,
            beta=best_beta,
            mae=best_mae,
            residual_stddev=pstdev(best_errors) if len(best_errors) > 1 else 0.0,
        )


def _fit(history: list[float], alpha: float, beta: float) -> tuple[float, float]:
    level = history[0]
    trend = history[1] - history[0] if len(history) > 1 else 0.0
    for value in history[1:]:
        previous_level = level
        level = alpha * value + (1 - alpha) * (level + trend)
        trend = beta * (level - previous_level) + (1 - beta) * trend
    return level, trend


def _walk_forward_errors(
    history: list[float], start: int, alpha: float, beta: float
) -> list[float]:
    level, trend = _fit(history[:start], alpha, beta)
    errors: list[float] = []
    for value in history[start:]:
        errors.append(value - max(0.0, level + trend))
        previous_level = level
        level = alpha * value + (1 - alpha) * (level + trend)
        trend = beta * (level - previous_level) + (1 - beta) * trend
    return errors


def _validate_model_fields(payload: dict[str, Any]) -> None:
    version = payload.get("version")
    feature_version = payload.get("feature_version")
    numeric = [
        payload.get("alpha"),
        payload.get("beta"),
        payload.get("mae"),
        payload.get("residual_stddev"),
    ]
    if not isinstance(version, str) or not version.strip() or len(version) > 120:
        raise ValueError("model version is invalid")
    if not isinstance(feature_version, str) or not feature_version.strip() or len(feature_version) > 80:
        raise ValueError("feature version is invalid")
    if any(not isinstance(value, (int, float)) or not math.isfinite(value) for value in numeric):
        raise ValueError("model metrics must be finite numbers")
    if not (0 < numeric[0] <= 1 and 0 <= numeric[1] <= 1):
        raise ValueError("model smoothing parameters are invalid")
    if numeric[2] < 0 or numeric[3] < 0:
        raise ValueError("model error metrics cannot be negative")


def create_model_store() -> ModelStore:
    return ModelStore(
        redis_url=os.getenv("REDIS_URL", "redis://localhost:6379/0"),
        model_key=os.getenv("MODEL_KEY", "forecast:model:active"),
    )
