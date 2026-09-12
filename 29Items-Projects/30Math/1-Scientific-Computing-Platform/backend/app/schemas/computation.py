"""Schemas for the saved-computation resource (CRUD) and plot/ml requests.

``ComputationCreate.input_payload`` is validated against a kind-specific model
so malformed jobs are rejected at the API boundary, not discovered by a worker.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator

ComputationKind = Literal["symbolic_solve", "integral", "ode", "plot", "ml_classify"]
ComputationStatus = Literal["queued", "running", "succeeded", "failed"]

_VARIABLE_PATTERN = r"^[a-zA-Z][a-zA-Z0-9_]{0,15}$"


class _ExpressionInput(BaseModel):
    """symbolic_solve / integral / ml_classify payloads."""

    model_config = ConfigDict(extra="forbid")

    expression: str = Field(..., min_length=1, max_length=512)
    variable: str = Field(default="x", pattern=_VARIABLE_PATTERN)


class _OdeInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    expression: str = Field(..., min_length=1, max_length=512, description="dy/dt = f(t, y)")
    t_start: float = 0.0
    t_end: float = 10.0
    y0: float = 1.0
    n_points: int = Field(default=200, ge=2, le=5000)

    @model_validator(mode="after")
    def check_span(self) -> _OdeInput:
        if self.t_start >= self.t_end:
            raise ValueError("t_start must be strictly less than t_end")
        return self


class _PlotInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    expression: str = Field(..., min_length=1, max_length=512)
    variable: str = Field(default="x", pattern=_VARIABLE_PATTERN)
    x_min: float = -10.0
    x_max: float = 10.0
    n_points: int = Field(default=1000, ge=2, le=5000)

    @model_validator(mode="after")
    def check_range(self) -> _PlotInput:
        if self.x_min >= self.x_max:
            raise ValueError("x_min must be strictly less than x_max")
        return self


_INPUT_MODELS: dict[str, type[BaseModel]] = {
    "symbolic_solve": _ExpressionInput,
    "integral": _ExpressionInput,
    "ml_classify": _ExpressionInput,
    "ode": _OdeInput,
    "plot": _PlotInput,
}


class ComputationCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    kind: ComputationKind
    input_payload: dict[str, Any] = Field(
        ..., description="Operation input, e.g. {'expression': 'x^2-4=0', 'variable': 'x'}"
    )

    @model_validator(mode="after")
    def validate_payload_for_kind(self) -> ComputationCreate:
        model = _INPUT_MODELS[self.kind]
        validated = model.model_validate(self.input_payload)
        # Store the normalized payload (defaults filled in) so workers always
        # see a complete, typed input.
        object.__setattr__(self, "input_payload", validated.model_dump())
        return self


class ComputationRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    title: str
    kind: ComputationKind
    status: ComputationStatus
    input_payload: dict[str, Any]
    result_payload: dict[str, Any] | None = None
    error_code: str | None = None
    created_at: datetime
    completed_at: datetime | None = None


class PlotFunctionRequest(BaseModel):
    expression: str = Field(..., min_length=1, max_length=512, examples=["sin(x)/x"])
    variable: str = Field(default="x", pattern=_VARIABLE_PATTERN)
    x_min: float = -10.0
    x_max: float = 10.0
    n_points: int = Field(default=1000, ge=2, le=5000)

    @model_validator(mode="after")
    def check_range(self) -> PlotFunctionRequest:
        if self.x_min >= self.x_max:
            raise ValueError("x_min must be strictly less than x_max")
        return self


class ClassifyRequest(BaseModel):
    expression: str = Field(..., min_length=1, max_length=512, examples=["3x^2 + 2x - 1"])


class ClassifyResponse(BaseModel):
    label: str
    confidence: float
    source: Literal["heuristic", "sagemaker"]
