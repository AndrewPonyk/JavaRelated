"""Text chunking: sentence-aware greedy packing with character overlap.

Better than a naive fixed window — it packs whole sentences up to ``size`` characters and
carries ``overlap`` characters of context into the next chunk so meaning isn't split mid-idea.
"""

from __future__ import annotations

import re

_SENTENCE_RE = re.compile(r"[^.!?]+[.!?]?", re.DOTALL)


def _sentences(text: str) -> list[str]:
    return [s.strip() for s in _SENTENCE_RE.findall(text) if s.strip()]


def chunk_text(text: str, *, size: int = 800, overlap: int = 100) -> list[str]:
    """Split ``text`` into overlapping, sentence-aligned chunks."""
    text = text.strip()
    if not text:
        return []
    if len(text) <= size:
        return [text]

    chunks: list[str] = []
    current = ""
    for sentence in _sentences(text):
        candidate = f"{current} {sentence}".strip() if current else sentence
        if len(candidate) <= size:
            current = candidate
            continue
        if current:
            chunks.append(current)
            # carry the tail of the previous chunk as overlap context.
            tail = current[-overlap:] if overlap > 0 else ""
            current = f"{tail} {sentence}".strip()
        else:
            # a single sentence longer than `size`: hard-split it.
            for i in range(0, len(sentence), size):
                chunks.append(sentence[i : i + size])
            current = ""
    if current:
        chunks.append(current)
    return chunks
