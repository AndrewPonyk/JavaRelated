"""Symbolic operations orchestration.

The service layer is the only code allowed to call sciengine / the DB / AWS.
Everything that can run unboundedly executes in the sciengine sandbox with the
API's sync budget; solve results are cached by canonical form.
"""

from __future__ import annotations

import logging
from dataclasses import asdict

import sympy as sp

from app.core.config import get_settings
from app.schemas.symbolic import (
    CalculusResponse,
    DifferentiateRequest,
    IntegrateRequest,
    LimitRequest,
    RenderRequest,
    RenderResponse,
    SeriesRequest,
    SolveRequest,
    SolveResponse,
)
from app.services.cache import computation_cache_key, get_cache
from sciengine.runtime import run_sandboxed
from sciengine.symbolic.latex import derivation_to_latex, expression_to_latex
from sciengine.symbolic.parsing import parse_equation

logger = logging.getLogger(__name__)


def solve(payload: SolveRequest) -> SolveResponse:
    """Solve within the sync budget. Raises ComputationTimeoutError past it —
    the endpoint decides whether that becomes a 202 job or a 504."""
    settings = get_settings()

    # Canonical-form cache key: "x^2-4=0" and "x**2 - 4 = 0" share one entry.
    # Parsing here is cheap and guarded; the *solve* is what needs the sandbox.
    equation = parse_equation(payload.expression)
    key = computation_cache_key("solve", sp.srepr(equation), payload.variable)

    cache = get_cache()
    cached_value = cache.get(key)
    if cached_value is not None:
        logger.info(
            "solve cache hit",
            extra={"extra_fields": {"computation_kind": "symbolic_solve", "cache_hit": True}},
        )
        return SolveResponse(**cached_value, cached=True)

    result = run_sandboxed(
        "solve_equation",
        payload.expression,
        payload.variable,
        timeout=settings.sync_solve_timeout_seconds,
    )
    response_fields = {
        **asdict(result),
        "derivation_latex": derivation_to_latex(result.steps_latex),
    }
    cache.set(key, response_fields, settings.solve_cache_ttl_seconds)
    logger.info(
        "solved equation",
        extra={"extra_fields": {"computation_kind": "symbolic_solve", "cache_hit": False}},
    )
    return SolveResponse(**response_fields, cached=False)


def differentiate(payload: DifferentiateRequest) -> CalculusResponse:
    result = run_sandboxed(
        "differentiate",
        payload.expression,
        payload.variable,
        order=payload.order,
        timeout=get_settings().sync_solve_timeout_seconds,
    )
    return CalculusResponse(**asdict(result))


def integrate(payload: IntegrateRequest) -> CalculusResponse:
    result = run_sandboxed(
        "integrate_symbolic",
        payload.expression,
        payload.variable,
        timeout=get_settings().sync_solve_timeout_seconds,
    )
    return CalculusResponse(**asdict(result))


def limit(payload: LimitRequest) -> CalculusResponse:
    result = run_sandboxed(
        "limit",
        payload.expression,
        payload.variable,
        to=payload.to,
        direction=payload.direction,
        timeout=get_settings().sync_solve_timeout_seconds,
    )
    return CalculusResponse(**asdict(result))


def series(payload: SeriesRequest) -> CalculusResponse:
    result = run_sandboxed(
        "taylor_series",
        payload.expression,
        payload.variable,
        around=payload.around,
        order=payload.order,
        timeout=get_settings().sync_solve_timeout_seconds,
    )
    return CalculusResponse(**asdict(result))


def render(payload: RenderRequest) -> RenderResponse:
    # Parse + print only: guarded, fast, no sandbox needed.
    return RenderResponse(latex=expression_to_latex(payload.expression))
