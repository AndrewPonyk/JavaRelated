"""FastAPI dependency providers (composition root).

Kept thin and centralized so endpoints declare *what* they need, not *how* it's built.
The embedding service is a lazily-created per-app singleton (heavy model + Redis client),
resolved from ``app.state`` so it works both under the app lifespan and in tests.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.db.session import get_session
from app.repositories.document_repository import DocumentRepository
from app.services.benchmark_service import BenchmarkService
from app.services.document_service import DocumentService
from app.services.embedding_service import EmbeddingService
from app.services.search_service import SearchService

SettingsDep = Annotated[Settings, Depends(get_settings)]
SessionDep = Annotated[AsyncSession, Depends(get_session)]


def get_embedder(request: Request) -> EmbeddingService:
    """Return the process-wide EmbeddingService, creating it on first use.

    The lifespan pre-warms this (and attaches a Redis client); tests hit the lazy path.
    """
    state = request.app.state
    embedder = getattr(state, "embedder", None)
    if embedder is None:
        embedder = EmbeddingService(get_settings(), redis=getattr(state, "redis", None))
        state.embedder = embedder
    return embedder


EmbedderDep = Annotated[EmbeddingService, Depends(get_embedder)]


def get_document_repository(session: SessionDep) -> DocumentRepository:
    return DocumentRepository(session)


RepositoryDep = Annotated[DocumentRepository, Depends(get_document_repository)]


def get_document_service(
    repository: RepositoryDep, embedder: EmbedderDep, settings: SettingsDep
) -> DocumentService:
    return DocumentService(repository, embedder, settings)


DocumentServiceDep = Annotated[DocumentService, Depends(get_document_service)]


def get_search_service(
    embedder: EmbedderDep, repository: RepositoryDep, settings: SettingsDep
) -> SearchService:
    return SearchService(embedder, repository, settings)


SearchServiceDep = Annotated[SearchService, Depends(get_search_service)]


def get_benchmark_service(search: SearchServiceDep) -> BenchmarkService:
    return BenchmarkService(search)


BenchmarkServiceDep = Annotated[BenchmarkService, Depends(get_benchmark_service)]
