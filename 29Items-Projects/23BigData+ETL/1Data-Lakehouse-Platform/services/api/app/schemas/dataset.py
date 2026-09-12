"""Request/response contracts. All input validation lives here — routers and
services can assume shapes are already correct."""

from __future__ import annotations

import json
import re
import uuid
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

# Dot-separated segments, each starting with a letter — this also guarantees
# every segment is a valid (quoted) Trino/Glue identifier, so previews can
# never hit an unquotable name.
NAME_PATTERN = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$")
NAME_MIN, NAME_MAX = 3, 120
EMAIL_PATTERN = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")

SCHEMA_MAX_COLUMNS = 500
SCHEMA_MAX_BYTES = 100_000


def _validate_name(value: str) -> str:
    if not (NAME_MIN <= len(value) <= NAME_MAX) or not NAME_PATTERN.fullmatch(value):
        raise ValueError(
            "must be dot-separated lowercase identifiers (each starting with a letter), "
            f"{NAME_MIN}-{NAME_MAX} chars, e.g. sales.orders"
        )
    return value


def _validate_email(value: str) -> str:
    if not EMAIL_PATTERN.fullmatch(value):
        raise ValueError("must be a valid email address")
    return value


class DatasetCreate(BaseModel):
    name: str = Field(examples=["sales.orders"], description="Catalog name: domain.table")
    layer: Literal["bronze", "silver", "gold"]
    description: str | None = Field(default=None, max_length=2000)
    owner_email: str = Field(examples=["data-eng@example.com"])
    s3_path: str = Field(examples=["s3://prod-lakehouse-silver/sales/orders"])

    @field_validator("name")
    @classmethod
    def name_is_valid(cls, value: str) -> str:
        return _validate_name(value)

    @field_validator("owner_email")
    @classmethod
    def email_looks_valid(cls, value: str) -> str:
        return _validate_email(value)

    @field_validator("s3_path")
    @classmethod
    def s3_path_scheme(cls, value: str) -> str:
        # file:// covers local/dev lakes (compose stack, e2e smoke)
        if not value.startswith(("s3://", "s3a://", "file://")):
            raise ValueError("must start with s3://, s3a:// or file://")
        return value.rstrip("/")


class DatasetUpdate(BaseModel):
    """Partial update — only provided fields change. Explicit nulls are rejected
    for non-nullable fields instead of surfacing as a database error."""

    description: str | None = Field(default=None, max_length=2000)
    owner_email: str | None = None

    @field_validator("owner_email")
    @classmethod
    def email_looks_valid(cls, value: str | None) -> str | None:
        if value is None:
            # Runs only when the client sent an explicit null (defaults skip
            # validation) — owner_email is required, so reject with a 422.
            raise ValueError("owner_email cannot be null; omit the field to keep it")
        return _validate_email(value)


class DatasetRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    layer: Literal["bronze", "silver", "gold"]
    description: str | None
    owner_email: str
    s3_path: str
    created_at: datetime
    updated_at: datetime


class DatasetList(BaseModel):
    items: list[DatasetRead]
    total: int
    limit: int
    offset: int


class DatasetVersionCreate(BaseModel):
    """Registered by pipelines after each contract change / promotion."""

    schema_json: dict[str, Any] = Field(
        examples=[{"order_id": "string", "amount": "decimal(18,2)"}],
        description="Column name → type mapping (or a full engine schema document)",
    )
    row_count: int | None = Field(default=None, ge=0)

    @field_validator("schema_json")
    @classmethod
    def schema_bounded(cls, value: dict[str, Any]) -> dict[str, Any]:
        if not value:
            raise ValueError("schema_json must not be empty")
        if len(value) > SCHEMA_MAX_COLUMNS:
            raise ValueError(f"schema_json exceeds {SCHEMA_MAX_COLUMNS} columns")
        if len(json.dumps(value)) > SCHEMA_MAX_BYTES:
            raise ValueError(f"schema_json exceeds {SCHEMA_MAX_BYTES} bytes")
        return value


class DatasetVersionRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    dataset_id: uuid.UUID
    version: int
    schema_json: dict[str, Any]
    row_count: int | None
    created_at: datetime


class AuditEventRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    occurred_at: datetime
    actor: str
    action: str
    entity_type: str
    entity_id: str
    details: dict[str, Any] | None


class AuditEventList(BaseModel):
    items: list[AuditEventRead]
    total: int
    limit: int
    offset: int


class PreviewResponse(BaseModel):
    columns: list[str]
    rows: list[list[Any]]
    row_count: int
    source: str = Field(description="Fully-qualified Trino table the preview was read from")
