"""Function plotting. Returns SVG bytes directly for interactive-size renders.

Large/batch renders take the other path: POST /computations with kind="plot"
queues the render on a worker, which stores the artifact (local dir or S3) and
serves it via GET /computations/{id}/artifact.
"""

from fastapi import APIRouter, Response

from app.schemas.computation import PlotFunctionRequest
from app.services import plot_service

router = APIRouter()


@router.post("/function", responses={200: {"content": {"image/svg+xml": {}}}})
def render_function_plot(payload: PlotFunctionRequest) -> Response:
    svg = plot_service.render_function_plot(payload)
    return Response(
        content=svg,
        media_type="image/svg+xml",
        headers={"Cache-Control": "private, max-age=3600"},
    )
