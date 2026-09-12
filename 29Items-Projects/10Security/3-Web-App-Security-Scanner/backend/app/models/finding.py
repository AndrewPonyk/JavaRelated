"""Finding ORM model — one normalized row per detected vulnerability.

Findings from every scanner (ZAP alerts, SQLMap injections, custom XSS hits)
are normalized into this single shape before persistence.
"""

from __future__ import annotations

import enum
from typing import TYPE_CHECKING

from sqlalchemy import JSON, Enum, ForeignKey, Index, String, Text, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin

if TYPE_CHECKING:
    from app.models.scan import Scan

# JSONB in production (PostgreSQL); plain JSON so the SQLite test DB compiles.
JSONVariant = JSONB().with_variant(JSON(), "sqlite")


class Severity(str, enum.Enum):
    # Ordered low→high; ML classifier predicts this label
    INFO = "info"
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class Source(str, enum.Enum):
    ZAP = "zap"
    SQLMAP = "sqlmap"
    XSS_ENGINE = "xss_engine"


class Finding(Base, TimestampMixin):
    __tablename__ = "findings"
    __table_args__ = (
        # dedupe: same scanner rule hitting same param on same URL = one finding
        UniqueConstraint("scan_id", "dedup_hash", name="uq_findings_scan_dedup"),
        Index("ix_findings_scan_severity", "scan_id", "severity"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    scan_id: Mapped[int] = mapped_column(ForeignKey("scans.id", ondelete="CASCADE"))

    source: Mapped[Source] = mapped_column(
        Enum(Source, name="finding_source", values_callable=lambda e: [m.value for m in e])
    )
    rule_id: Mapped[str] = mapped_column(String(128))  # ZAP pluginId / CWE / XSS-CTX
    title: Mapped[str] = mapped_column(String(512))
    description: Mapped[str | None] = mapped_column(Text)
    url: Mapped[str] = mapped_column(String(2048))
    param: Mapped[str | None] = mapped_column(String(255))
    method: Mapped[str | None] = mapped_column(String(10))

    severity: Mapped[Severity] = mapped_column(
        Enum(Severity, name="finding_severity", values_callable=lambda e: [m.value for m in e])
    )
    severity_confidence: Mapped[float | None] = mapped_column()  # ML confidence 0..1
    owasp_category: Mapped[str | None] = mapped_column(String(8))  # "A01:2021" …
    cwe_id: Mapped[int | None] = mapped_column(String(16))  # "CWE-79"

    dedup_hash: Mapped[str] = mapped_column(String(64))  # sha256 hex
    evidence: Mapped[dict] = mapped_column(JSONVariant, default=dict)  # redacted req/resp excerpts
    raw: Mapped[dict] = mapped_column(JSONVariant, default=dict)  # scanner-native payload

    scan: Mapped[Scan] = relationship(back_populates="findings")

    @staticmethod
    def compute_dedup_hash(source: str, rule_id: str, url: str, param: str | None) -> str:
        """Stable dedup key — must match orchestrator normalization."""
        import hashlib

        material = f"{source}|{rule_id}|{url}|{param or ''}"
        return hashlib.sha256(material.encode()).hexdigest()
