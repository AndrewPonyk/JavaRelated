"""Shared FastAPI dependencies: DB session, authenticated principal, services."""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends, Header, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import AuthError, Principal, verify_token
from app.db.session import get_session
from app.services.document_service import DocumentService
from app.services.rag_service import RAGService

SessionDep = Annotated[AsyncSession, Depends(get_session)]


async def get_principal(
    authorization: Annotated[str | None, Header()] = None,
) -> Principal:
    """Resolve the caller from the ``Authorization: Bearer <jwt>`` header."""
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "missing bearer token")
    try:
        return verify_token(authorization.split(" ", 1)[1])
    except AuthError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, str(exc)) from exc


PrincipalDep = Annotated[Principal, Depends(get_principal)]


def get_document_service(session: SessionDep) -> DocumentService:
    return DocumentService(session)


def get_rag_service(session: SessionDep) -> RAGService:
    return RAGService(session)


DocumentServiceDep = Annotated[DocumentService, Depends(get_document_service)]
RAGServiceDep = Annotated[RAGService, Depends(get_rag_service)]
