from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Index, Integer, String, Text, func
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class AnalysisJobEntity(Base):
    __tablename__ = "analysis_jobs"
    __table_args__ = (
        Index("idx_analysis_jobs_status", "status"),
        Index("idx_analysis_jobs_created_at", "created_at"),
        Index("idx_analysis_jobs_rule_pack", "rule_pack"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    project_name: Mapped[str] = mapped_column(String(120), nullable=False)
    source_path: Mapped[str] = mapped_column(String(500), nullable=False)
    compile_commands_path: Mapped[str | None] = mapped_column(String(500))
    rule_pack: Mapped[str] = mapped_column(String(80), nullable=False, default="default")
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="queued")
    ast_facts_json: Mapped[str] = mapped_column(Text, nullable=False, default="{}")
    error_message: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.current_timestamp(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.current_timestamp(),
        onupdate=func.current_timestamp(),
        nullable=False,
    )

    findings: Mapped[list["FindingEntity"]] = relationship(
        back_populates="analysis", cascade="all, delete-orphan", passive_deletes=True
    )


class FindingEntity(Base):
    __tablename__ = "findings"
    __table_args__ = (
        Index("idx_findings_analysis_id", "analysis_id"),
        Index("idx_findings_rule_id", "rule_id"),
        Index("idx_findings_severity", "severity"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    analysis_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("analysis_jobs.id", ondelete="CASCADE"), nullable=False
    )
    rule_id: Mapped[str] = mapped_column(String(160), nullable=False)
    severity: Mapped[str] = mapped_column(String(20), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    file_path: Mapped[str] = mapped_column(String(500), nullable=False)
    line: Mapped[int] = mapped_column(Integer, nullable=False)
    column: Mapped[int] = mapped_column(Integer, nullable=False)
    evidence_json: Mapped[str] = mapped_column(Text, nullable=False, default="{}")

    analysis: Mapped[AnalysisJobEntity] = relationship(back_populates="findings")


class RuleDefinitionEntity(Base):
    __tablename__ = "rule_definitions"
    __table_args__ = (Index("idx_rule_definitions_pack", "rule_pack"),)

    id: Mapped[str] = mapped_column(String(160), primary_key=True)
    rule_pack: Mapped[str] = mapped_column(String(80), nullable=False)
    severity: Mapped[str] = mapped_column(String(20), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    enabled: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
