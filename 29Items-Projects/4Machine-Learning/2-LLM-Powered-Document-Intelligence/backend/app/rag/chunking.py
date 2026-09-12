"""Document chunking — native recursive character splitter (no external dependency).

Splits on the largest natural boundary that keeps chunks under ``chunk_size`` (paragraphs
→ lines → sentences → words), then stitches pieces together with overlap so a clause split
across a boundary stays retrievable. Metadata is carried onto every chunk for filtering and
citation. Equivalent in spirit to LangChain's ``RecursiveCharacterTextSplitter``.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from app.core.config import settings

_SEPARATORS = ["\n\n", "\n", ". ", " ", ""]


@dataclass
class Chunk:
    """A unit of indexed text plus metadata for filtering and citation."""

    text: str
    metadata: dict = field(default_factory=dict)


def _split_recursive(text: str, chunk_size: int, separators: list[str]) -> list[str]:
    """Recursively split ``text`` into pieces no larger than ``chunk_size``."""
    if len(text) <= chunk_size:
        return [text] if text else []

    sep = separators[0] if separators else ""
    rest = separators[1:] if len(separators) > 1 else [""]

    if sep == "":
        # Hard cut at chunk_size as a last resort.
        return [text[i : i + chunk_size] for i in range(0, len(text), chunk_size)]

    pieces = text.split(sep)
    out: list[str] = []
    for piece in pieces:
        if len(piece) <= chunk_size:
            out.append(piece)
        else:
            out.extend(_split_recursive(piece, chunk_size, rest))
    return [p for p in out if p]


def _merge_with_overlap(pieces: list[str], chunk_size: int, overlap: int) -> list[str]:
    """Greedily merge small pieces up to ``chunk_size`` and add trailing overlap."""
    chunks: list[str] = []
    current = ""
    for piece in pieces:
        candidate = f"{current} {piece}".strip() if current else piece
        if len(candidate) <= chunk_size:
            current = candidate
            continue
        if current:
            chunks.append(current)
        # Seed the next chunk with the overlap tail of the previous one.
        tail = current[-overlap:] if overlap and current else ""
        current = f"{tail} {piece}".strip() if tail else piece
    if current:
        chunks.append(current)
    return chunks


def split_document(text: str, *, base_metadata: dict | None = None) -> list[Chunk]:
    """Split raw document text into overlapping, metadata-tagged chunks."""
    text = (text or "").strip()
    if not text:
        return []
    pieces = _split_recursive(text, settings.rag_chunk_size, _SEPARATORS)
    merged = _merge_with_overlap(pieces, settings.rag_chunk_size, settings.rag_chunk_overlap)
    base = base_metadata or {}
    return [Chunk(text=part, metadata={**base, "chunk_index": i}) for i, part in enumerate(merged)]
