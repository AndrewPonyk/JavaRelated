"""Request/response models for the symbolic endpoints.

Pydantic validates *shape* here; sciengine's parser validates *content*
(whitelist, complexity). Both run before any computation happens.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator

_VARIABLE_PATTERN = r"^[a-zA-Z][a-zA-Z0-9_]{0,15}$"

_EXPRESSION_FIELD = Field(
    ...,
    min_length=1,
    max_length=512,
    description="Equation like 'x^2 - 4 = 0' (a bare expression implies '= 0').",
    examples=["x^2 - 4 = 0", "sin(x) = 1/2"],
)


class _ExpressionRequest(BaseModel):
    expression: str = _EXPRESSION_FIELD
    variable: str = Field(default="x", pattern=_VARIABLE_PATTERN)

    @field_validator("expression")
    @classmethod
    def cheap_reject_dunder(cls, v: str) -> str:
        # Belt-and-braces: the real gate is sciengine.symbolic.parsing.
        if "__" in v:
            raise ValueError("expression contains forbidden token '__'")
        return v


class SolveRequest(_ExpressionRequest):
    pass


class SolveResponse(BaseModel):
    equation_latex: str
    variable: str
    solutions: list[str]
    solutions_latex: list[str]
    steps_latex: list[str] = []
    derivation_latex: str = ""  # steps as one KaTeX-ready aligned block
    cached: bool = False


class SolveQueuedResponse(BaseModel):
    """202 body when a solve exceeded the sync budget and became a job."""

    computation_id: str
    status: Literal["queued", "running", "succeeded", "failed"]
    detail: str


class DifferentiateRequest(_ExpressionRequest):
    order: int = Field(default=1, ge=1, le=10)


class IntegrateRequest(_ExpressionRequest):
    pass


class LimitRequest(_ExpressionRequest):
    to: str = Field(default="0", min_length=1, max_length=64, examples=["0", "oo", "pi/2"])
    direction: Literal["+", "-"] = "+"


class SeriesRequest(_ExpressionRequest):
    around: str = Field(default="0", min_length=1, max_length=64)
    order: int = Field(default=6, ge=1, le=12)


class CalculusResponse(BaseModel):
    input_latex: str
    result_text: str
    result_latex: str
    operation: str


class RenderRequest(BaseModel):
    expression: str = _EXPRESSION_FIELD


class RenderResponse(BaseModel):
    latex: str
