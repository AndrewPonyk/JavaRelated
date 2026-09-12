"""Typed RAG-layer errors.

Centralized so every layer (services, API exception handlers) can branch on a stable
hierarchy. Adapters raise these; the API maps them to clean HTTP responses.
"""

from __future__ import annotations


class RAGError(Exception):
    """Base class for all RAG-layer failures."""


class LLMError(RAGError):
    """Raised when a chat-model invocation fails."""


class EmbeddingError(RAGError):
    """Raised when embedding generation fails."""


class RetrievalError(RAGError):
    """Raised when a vector search or upsert fails."""


class IngestionError(RAGError):
    """Raised when document loading/parsing/indexing fails."""


class RefusalError(LLMError):
    """Raised when the model declines to answer for safety reasons."""
