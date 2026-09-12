"""Document parsing: raw bytes → plain text.

Dispatches on file extension. PDF/DOCX parsers are imported lazily so the common
text/markdown path needs no extra dependencies; a missing optional parser raises a clear
:class:`IngestionError` rather than a cryptic ImportError.
"""

from __future__ import annotations

import io

from app.rag.errors import IngestionError


def parse_bytes(content: bytes, filename: str) -> str:
    """Extract plain text from ``content`` based on ``filename``'s extension."""
    name = filename.lower()
    if name.endswith((".txt", ".md", ".markdown")):
        return content.decode("utf-8", errors="replace")
    if name.endswith(".pdf"):
        return _parse_pdf(content)
    if name.endswith(".docx"):
        return _parse_docx(content)
    # Default: best-effort UTF-8 decode (covers .json, .csv, unknown text formats).
    return content.decode("utf-8", errors="replace")


def _parse_pdf(content: bytes) -> str:
    try:
        from pypdf import PdfReader
    except ImportError as exc:  # pragma: no cover - optional dep
        raise IngestionError("PDF support requires 'pypdf' (pip install pypdf)") from exc
    reader = PdfReader(io.BytesIO(content))
    return "\n\n".join((page.extract_text() or "") for page in reader.pages)


def _parse_docx(content: bytes) -> str:
    try:
        import docx  # python-docx
    except ImportError as exc:  # pragma: no cover - optional dep
        raise IngestionError(
            "DOCX support requires 'python-docx' (pip install python-docx)"
        ) from exc
    document = docx.Document(io.BytesIO(content))
    return "\n".join(p.text for p in document.paragraphs)
