"""Plot orchestration: sciengine renders (inside the sandbox); this layer owns
delivery policy. Interactive renders return bytes; batch/heavy renders go
through the computations queue and land in the artifact store instead.
"""

from __future__ import annotations

from app.core.config import get_settings
from app.schemas.computation import PlotFunctionRequest
from sciengine.runtime import run_sandboxed


def render_function_plot(payload: PlotFunctionRequest) -> bytes:
    return run_sandboxed(
        "plot_expression",
        payload.expression,
        payload.variable,
        payload.x_min,
        payload.x_max,
        n_points=payload.n_points,
        fmt="svg",
        timeout=get_settings().sync_solve_timeout_seconds,
    )
