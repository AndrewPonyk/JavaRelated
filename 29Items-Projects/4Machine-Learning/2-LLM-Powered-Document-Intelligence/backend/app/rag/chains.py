"""Backwards-compatible chain accessors.

The orchestration now lives in :mod:`app.rag.pipeline` (provider-agnostic, works for both
the local and bedrock backends). These thin helpers preserve the names referenced in the
docs while delegating to the pipeline.
"""

from __future__ import annotations

from app.rag.pipeline import RagPipeline, get_pipeline

__all__ = ["RagPipeline", "get_pipeline", "build_qa_pipeline"]


def build_qa_pipeline() -> RagPipeline:
    """Return the RAG pipeline used for question answering and summarization."""
    return get_pipeline()
