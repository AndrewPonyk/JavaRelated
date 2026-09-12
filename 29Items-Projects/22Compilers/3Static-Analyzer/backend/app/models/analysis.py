from datetime import datetime
from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class AnalysisStatus(StrEnum):
    queued = "queued"
    running = "running"
    completed = "completed"
    failed = "failed"


class FindingSeverity(StrEnum):
    info = "info"
    low = "low"
    medium = "medium"
    high = "high"
    critical = "critical"


class SourceLocation(BaseModel):
    file_path: str
    line: int = Field(ge=1)
    column: int = Field(ge=1)


class Finding(BaseModel):
    id: UUID
    rule_id: str
    severity: FindingSeverity
    message: str
    file_path: str
    line: int = Field(ge=1)
    column: int = Field(ge=1)
    evidence: dict = Field(default_factory=dict)


class AnalysisCreate(BaseModel):
    project_name: str = Field(min_length=1, max_length=120)
    source_path: str = Field(min_length=1, max_length=500)
    compile_commands_path: str | None = Field(default=None, max_length=500)
    rule_pack: str = Field(default="default", min_length=1, max_length=80)
    source_code: str | None = Field(default=None, max_length=1_000_000)

    @field_validator("source_path", "compile_commands_path")
    @classmethod
    def reject_control_characters(cls, value: str | None) -> str | None:
        if value is not None and any(ord(character) < 32 for character in value):
            raise ValueError("path contains control characters")
        return value


class AnalysisUpdate(BaseModel):
    project_name: str | None = Field(default=None, min_length=1, max_length=120)
    rule_pack: str | None = Field(default=None, min_length=1, max_length=80)
    status: AnalysisStatus | None = None


class AnalysisRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    project_name: str
    source_path: str
    compile_commands_path: str | None = None
    rule_pack: str
    status: AnalysisStatus
    findings: list[Finding] = Field(default_factory=list)
    ast_facts: dict = Field(default_factory=dict)
    error_message: str | None = None
    created_at: datetime
    updated_at: datetime


class AnalysisListItem(BaseModel):
    id: UUID
    project_name: str
    source_path: str
    rule_pack: str
    status: AnalysisStatus
    finding_count: int
    highest_severity: FindingSeverity | None = None
    created_at: datetime
    updated_at: datetime


class RuleDefinition(BaseModel):
    id: str
    rule_pack: str
    severity: FindingSeverity
    description: str
    enabled: bool


class HealthRead(BaseModel):
    status: str
    database: str
    rules_loaded: int
