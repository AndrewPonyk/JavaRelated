"""FastAPI dependency wiring: repositories, services, and auth."""

from collections.abc import Callable
from typing import Annotated

import jwt
from fastapi import Depends, Header, HTTPException, status

from app.core.security import decode_access_token
from app.db.neo4j_client import get_driver
from app.db.repositories.drug_repository import DrugRepository
from app.db.repositories.interaction_repository import InteractionRepository
from app.services.drug_service import DrugService
from app.services.interaction_service import InteractionService
from app.services.ml_severity_service import MLSeverityService
from app.services.rxnorm_service import RxNormService

# Stateless, cache-holding services are process-wide singletons.
_rxnorm_service = RxNormService()
_ml_service = MLSeverityService()


def get_rxnorm_service() -> RxNormService:
    return _rxnorm_service


def get_ml_service() -> MLSeverityService:
    return _ml_service


def get_drug_repository() -> DrugRepository:
    return DrugRepository(get_driver())


def get_interaction_repository() -> InteractionRepository:
    return InteractionRepository(get_driver())


def get_drug_service(
    repo: Annotated[DrugRepository, Depends(get_drug_repository)],
    rxnorm: Annotated[RxNormService, Depends(get_rxnorm_service)],
) -> DrugService:
    return DrugService(repo, rxnorm)


def get_interaction_service(
    interaction_repo: Annotated[InteractionRepository, Depends(get_interaction_repository)],
    drug_repo: Annotated[DrugRepository, Depends(get_drug_repository)],
    rxnorm: Annotated[RxNormService, Depends(get_rxnorm_service)],
    ml: Annotated[MLSeverityService, Depends(get_ml_service)],
) -> InteractionService:
    return InteractionService(interaction_repo, drug_repo, rxnorm, ml)


async def get_current_principal(
    authorization: Annotated[str | None, Header()] = None,
) -> dict:
    """Validate a bearer token.

    Uses shared-secret JWT here; in production swap for OIDC/JWKS validation
    (AWS Cognito / Okta) — the call site (this dependency) stays the same.
    """
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")
    token = authorization.split(" ", 1)[1]
    try:
        return decode_access_token(token)
    except jwt.PyJWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token"
        ) from exc


def _scopes_from_claims(principal: dict) -> set[str]:
    scope = principal.get("scope", "")
    if isinstance(scope, str):
        return set(scope.split())
    if isinstance(scope, list | tuple):
        return set(scope)
    return set()


def require_scopes(*required: str) -> Callable:
    """Dependency factory enforcing that the principal holds all given scopes."""

    async def checker(
        principal: Annotated[dict, Depends(get_current_principal)],
    ) -> dict:
        granted = _scopes_from_claims(principal)
        missing = set(required) - granted
        if missing:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Missing required scope(s): {', '.join(sorted(missing))}",
            )
        return principal

    return checker
