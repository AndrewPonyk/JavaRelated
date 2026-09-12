"""Symbolic math endpoints.

Endpoints stay thin: validation via Pydantic, one service call, status-code
decisions. SciEngineError → problem+json mapping happens globally in app.main.

Concurrency: sciengine calls block on the sandbox pipe, so they run through
``anyio.to_thread`` — the event loop stays free while math executes in the
sandboxed subprocess (docs/ARCHITECTURE.md §2.4).

Auto-routing (§2.3): a solve that exceeds the sync budget becomes a queued
Computation for authenticated users (202 + id to poll); anonymous callers get
the 504 problem with guidance.
"""

from functools import partial
from typing import cast

from anyio import to_thread
from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.api.deps import OptionalUser, SessionDep
from app.schemas.computation import ComputationCreate, ComputationStatus
from app.schemas.symbolic import (
    CalculusResponse,
    DifferentiateRequest,
    IntegrateRequest,
    LimitRequest,
    RenderRequest,
    RenderResponse,
    SeriesRequest,
    SolveQueuedResponse,
    SolveRequest,
    SolveResponse,
)
from app.services import computation_service, symbolic_service
from sciengine.exceptions import ComputationTimeoutError

router = APIRouter()


@router.post(
    "/solve",
    response_model=SolveResponse,
    responses={
        202: {
            "model": SolveQueuedResponse,
            "description": "Solve exceeded the sync budget; queued as a computation "
            "(authenticated callers only). Poll GET /computations/{id}.",
        }
    },
)
async def solve_equation(payload: SolveRequest, user: OptionalUser, session: SessionDep):
    try:
        return await to_thread.run_sync(partial(symbolic_service.solve, payload))
    except ComputationTimeoutError:
        if user is None:
            raise  # → 504 problem+json; detail below guides sign-in
        computation = await computation_service.create(
            session,
            owner_id=user.user_id,
            data=ComputationCreate(
                title=f"Solve: {payload.expression[:60]}",
                kind="symbolic_solve",
                input_payload={"expression": payload.expression, "variable": payload.variable},
            ),
        )
        body = SolveQueuedResponse(
            # ORM stores status as str; values are constrained to this Literal.
            computation_id=str(computation.id),
            status=cast(ComputationStatus, computation.status),
            detail="Solve exceeded the interactive budget and is running as a background job.",
        )
        return JSONResponse(status_code=202, content=body.model_dump())


@router.post("/differentiate", response_model=CalculusResponse)
async def differentiate(payload: DifferentiateRequest) -> CalculusResponse:
    return await to_thread.run_sync(partial(symbolic_service.differentiate, payload))


@router.post("/integrate", response_model=CalculusResponse)
async def integrate(payload: IntegrateRequest) -> CalculusResponse:
    return await to_thread.run_sync(partial(symbolic_service.integrate, payload))


@router.post("/limit", response_model=CalculusResponse)
async def limit(payload: LimitRequest) -> CalculusResponse:
    return await to_thread.run_sync(partial(symbolic_service.limit, payload))


@router.post("/series", response_model=CalculusResponse)
async def series(payload: SeriesRequest) -> CalculusResponse:
    return await to_thread.run_sync(partial(symbolic_service.series, payload))


@router.post("/render", response_model=RenderResponse)
async def render_latex(payload: RenderRequest) -> RenderResponse:
    """Canonical LaTeX for a user-typed expression (live preview in the UI)."""
    return await to_thread.run_sync(partial(symbolic_service.render, payload))
