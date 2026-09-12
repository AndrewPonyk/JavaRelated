"""Domain types and provider protocols for the RAG layer.

The RAG core is defined against these protocols, not against any concrete vendor SDK.
Two backends implement them (selected by ``settings.rag_backend``):

* ``local``   — dependency-light, fully functional (deterministic embeddings, in-memory
                cosine search, extractive grounded answering). Used for dev/docker/tests.
* ``bedrock`` — Claude on Amazon Bedrock + Pinecone via LangChain (production).

Because everything depends only on these protocols, the service layer and tests never
import a vendor SDK.
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Protocol, runtime_checkable


@dataclass(frozen=True)
class VectorRecord:
    """A vector to upsert into the store."""

    id: str
    vector: list[float]
    text: str
    metadata: dict = field(default_factory=dict)


@dataclass(frozen=True)
class RetrievedChunk:
    """A chunk returned from similarity search."""

    id: str
    text: str
    score: float
    metadata: dict = field(default_factory=dict)

    @property
    def document_id(self) -> str:
        return str(self.metadata.get("document_id", ""))

    @property
    def chunk_index(self) -> int:
        return int(self.metadata.get("chunk_index", 0))


@dataclass(frozen=True)
class Answer:
    """A synthesized answer plus the chunks it was grounded on."""

    text: str
    citations: list[RetrievedChunk]
    model_id: str


@runtime_checkable
class Embedder(Protocol):
    """Turns text into vectors. Query and document embeddings may differ by backend."""

    dimension: int

    async def aembed_documents(self, texts: list[str]) -> list[list[float]]: ...

    async def aembed_query(self, text: str) -> list[float]: ...


@runtime_checkable
class VectorStore(Protocol):
    """Namespaced (per-tenant) vector index."""

    async def aupsert(self, namespace: str, records: list[VectorRecord]) -> None: ...

    async def aquery(
        self,
        namespace: str,
        vector: list[float],
        top_k: int,
        flt: dict | None = None,
    ) -> list[RetrievedChunk]: ...

    async def adelete_document(self, namespace: str, document_id: str) -> None: ...


@runtime_checkable
class ChatModel(Protocol):
    """A chat/completion model used for synthesis and summarization."""

    model_id: str

    async def agenerate(self, system: str, prompt: str) -> str: ...

    def astream(self, system: str, prompt: str) -> AsyncIterator[str]: ...
