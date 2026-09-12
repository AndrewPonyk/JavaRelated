"""Model catalog, A/B configuration and promotion endpoints."""

from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Depends, status
from fastapi.responses import Response
from sqlalchemy.orm import Session

from fraud_detection.api.deps import get_ab_config_store, get_db_session, get_model_loader
from fraud_detection.core.config import get_settings
from fraud_detection.core.errors import ConflictError, NotFoundError
from fraud_detection.core.logging import get_logger
from fraud_detection.db.repositories import ModelVersionRepository
from fraud_detection.ml.registry import LocalModelStore, MLflowRegistry
from fraud_detection.monitoring.metrics import FRAUD_DB_ERRORS_TOTAL
from fraud_detection.schemas.model import (
    ABConfig,
    ABConfigUpdate,
    ModelAliases,
    ModelListResponse,
    ModelVersionInfo,
    PromoteRequest,
    PromoteResponse,
)
from fraud_detection.services.ab_config import ABConfigStore
from fraud_detection.services.model_loader import ModelLoader

logger = get_logger(__name__)

router = APIRouter(prefix="/models", tags=["models"])


def _parse_trained_at(value: str | None) -> datetime | None:
    """Parse the local store's trained_at ISO string, tolerating absence."""
    if not value:
        return None
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None


def _catalog(session: Session | None) -> tuple[list[ModelVersionInfo], ModelAliases]:
    """Merge the local model store with the DB catalog (local wins per version)."""
    settings = get_settings()
    store = LocalModelStore(settings.model_dir)
    aliases = store.get_aliases()

    items: list[ModelVersionInfo] = []
    local_versions: set[str] = set()
    for entry in store.list_versions():
        items.append(
            ModelVersionInfo(
                name=settings.model_name,
                version=entry["version"],
                stage=entry["stage"],
                auc=entry.get("auc"),
                registered_at=_parse_trained_at(entry.get("trained_at")),
                source="local",
            )
        )
        local_versions.add(entry["version"])

    if session is not None:
        try:
            for row in ModelVersionRepository(session).list():
                if row.version not in local_versions:
                    items.append(
                        ModelVersionInfo(
                            name=row.name,
                            version=row.version,
                            stage=row.stage,
                            auc=row.auc,
                            registered_at=row.registered_at,
                            source="mlflow",
                        )
                    )
        except Exception:  # noqa: BLE001 - policy: catalog reads survive DB outages
            session.rollback()
            FRAUD_DB_ERRORS_TOTAL.inc()
            logger.warning("model_catalog_db_read_failed")

    return items, ModelAliases(**aliases)


@router.get("", response_model=ModelListResponse)
def list_models(session: Session = Depends(get_db_session)) -> ModelListResponse:
    """List every known model version plus the current alias mapping."""
    items, aliases = _catalog(session)
    return ModelListResponse(items=items, aliases=aliases)


@router.get("/ab-config", response_model=ABConfig)
def get_ab_config(store: ABConfigStore = Depends(get_ab_config_store)) -> ABConfig:
    """Return the effective champion/challenger traffic-split configuration."""
    settings = get_settings()
    enabled, traffic_split = store.get()
    aliases = LocalModelStore(settings.model_dir).get_aliases()
    return ABConfig(
        enabled=enabled,
        traffic_split=traffic_split,
        model_name=settings.model_name,
        champion_version=aliases.get("champion"),
        challenger_version=aliases.get("challenger"),
    )


@router.put("/ab-config", response_model=ABConfig)
def update_ab_config(
    payload: ABConfigUpdate,
    store: ABConfigStore = Depends(get_ab_config_store),
) -> ABConfig:
    """Update the experiment configuration at runtime (per-process override)."""
    store.update(enabled=payload.enabled, traffic_split=payload.traffic_split)
    return get_ab_config(store)


@router.post("/promote", response_model=PromoteResponse)
def promote_model(
    payload: PromoteRequest,
    session: Session = Depends(get_db_session),
    loader: ModelLoader = Depends(get_model_loader),
) -> PromoteResponse:
    """Point an alias at a model version (e.g. promote challenger to champion)."""
    settings = get_settings()
    store = LocalModelStore(settings.model_dir)
    try:
        previous = store.set_alias(payload.alias, payload.version)
    except KeyError:
        raise NotFoundError(f"unknown model version {payload.version!r}") from None

    try:
        repo = ModelVersionRepository(session)
        repo.update_stage(payload.version, payload.alias)
        if payload.alias == "champion" and previous and previous != payload.version:
            demoted = repo.get_by_version(previous)
            if demoted is not None and demoted.stage == "champion":
                demoted.stage = "archived"
    except Exception:  # noqa: BLE001 - policy: catalog sync is best-effort
        session.rollback()
        FRAUD_DB_ERRORS_TOTAL.inc()
        logger.warning("model_stage_db_sync_failed", version=payload.version)

    if MLflowRegistry.available():
        try:
            MLflowRegistry().promote(payload.version, payload.alias)
        except Exception:  # noqa: BLE001 - policy: MLflow tier is additive
            logger.warning("mlflow_promotion_failed", version=payload.version)

    loader.invalidate()
    return PromoteResponse(version=payload.version, alias=payload.alias, previous=previous)


@router.get("/{version}", response_model=ModelVersionInfo)
def get_model_version(version: str, session: Session = Depends(get_db_session)) -> ModelVersionInfo:
    """Return one model version from the merged catalog."""
    items, _ = _catalog(session)
    for item in items:
        if item.version == version:
            return item
    raise NotFoundError(f"unknown model version {version!r}")


@router.delete("/{version}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_model_version(
    version: str,
    session: Session = Depends(get_db_session),
    loader: ModelLoader = Depends(get_model_loader),
) -> Response:
    """Delete an archived model version (the champion is protected)."""
    settings = get_settings()
    store = LocalModelStore(settings.model_dir)
    try:
        store.delete_version(version)
    except KeyError:
        raise NotFoundError(f"unknown model version {version!r}") from None
    except ValueError as exc:
        raise ConflictError(str(exc)) from None

    try:
        ModelVersionRepository(session).delete(version)
    except Exception:  # noqa: BLE001 - policy: catalog sync is best-effort
        session.rollback()
        FRAUD_DB_ERRORS_TOTAL.inc()
        logger.warning("model_delete_db_sync_failed", version=version)

    loader.invalidate()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
