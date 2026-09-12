"""Shared FastAPI dependencies (auth, session, repository, services).

Centralizes dependency-injection wiring so routers stay declarative. Each
request gets its own DB session and repository; services are constructed per
request over that repository. The token verifier and the serving warm-pool are
process-wide (the pool lives in ``serving_service``).
"""
from __future__ import annotations

from functools import lru_cache
from typing import Annotated

from fastapi import Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.config import Settings, get_settings
from src.core.errors import AuthenticationError
from src.core.security import HS256TokenVerifier, Principal, TokenVerifier
from src.db.repository import SqlAlchemyRepository
from src.db.session import get_session
from src.services.ab_testing_service import ABTestingService
from src.services.drift_service import DriftService
from src.services.experiment_service import ExperimentService
from src.services.model_registry_service import ModelRegistryService
from src.services.serving_service import ServingService

# Scopes granted when auth_mode='disabled' (non-prod only; prod config rejects it).
_DEV_SCOPES = frozenset(
    {"experiments:read", "experiments:write", "models:promote", "serving:invoke"}
)


@lru_cache
def get_token_verifier() -> TokenVerifier | None:
    """Build the configured token verifier once (None when auth is disabled)."""
    settings: Settings = get_settings()
    if settings.auth_mode == "disabled":
        return None
    if settings.auth_mode == "hs256":
        return HS256TokenVerifier(
            secret=settings.jwt_secret,
            audience=settings.oidc_audience,
            issuer=settings.oidc_issuer,
        )
    # TODO: OIDC RS256/JWKS verifier implementing the same TokenVerifier interface.
    raise NotImplementedError("OIDC verifier not yet wired; use auth_mode=hs256 for now")


async def get_current_principal(
    authorization: Annotated[str | None, Header()] = None,
) -> Principal:
    """Resolve the authenticated caller from the ``Authorization`` header."""
    verifier = get_token_verifier()
    if verifier is None:  # auth disabled (dev/test only)
        return Principal(subject="dev", email="dev@example.com", scopes=_DEV_SCOPES)

    if not authorization or not authorization.lower().startswith("bearer "):
        raise AuthenticationError("missing bearer token")
    token = authorization[7:].strip()
    if not token:
        raise AuthenticationError("empty bearer token")
    return verifier.verify(token)


def get_repository(
    session: Annotated[AsyncSession, Depends(get_session)],
) -> SqlAlchemyRepository:
    return SqlAlchemyRepository(session)


Repo = Annotated[SqlAlchemyRepository, Depends(get_repository)]


def get_experiment_service(repo: Repo) -> ExperimentService:
    return ExperimentService(repo)


def get_model_registry_service(repo: Repo) -> ModelRegistryService:
    return ModelRegistryService(repo)


def get_serving_service(repo: Repo) -> ServingService:
    return ServingService(repo)


def get_ab_service(repo: Repo) -> ABTestingService:
    return ABTestingService(repo)


def get_drift_service(repo: Repo) -> DriftService:
    return DriftService(repo)


CurrentPrincipal = Annotated[Principal, Depends(get_current_principal)]
