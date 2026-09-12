"""Per-kind computation execution. Pure logic — no Celery, no HTTP — so it is
unit-testable and the Celery task stays a thin persistence wrapper.

Every kind runs inside the sciengine sandbox (killable subprocess) with the
worker budget; results are JSON-safe dicts ready for ``result_payload``.
"""

from __future__ import annotations

from dataclasses import asdict

from app.core.config import get_settings
from app.services.artifact_store import get_artifact_store
from sciengine.runtime import run_sandboxed


def execute_computation(kind: str, input_payload: dict, computation_id: str) -> dict:
    executor = _EXECUTORS.get(kind)
    if executor is None:
        raise ValueError(f"Unknown computation kind: {kind!r}")
    return executor(input_payload, computation_id)


def _budget() -> float:
    return get_settings().worker_op_timeout_seconds


def _execute_symbolic_solve(payload: dict, computation_id: str) -> dict:
    result = run_sandboxed(
        "solve_equation",
        payload["expression"],
        payload.get("variable", "x"),
        timeout=_budget(),
    )
    return asdict(result)


def _execute_integral(payload: dict, computation_id: str) -> dict:
    result = run_sandboxed(
        "integrate_symbolic",
        payload["expression"],
        payload.get("variable", "x"),
        timeout=_budget(),
    )
    return asdict(result)


def _execute_ode(payload: dict, computation_id: str) -> dict:
    return run_sandboxed(
        "solve_ivp_expression",
        payload["expression"],
        (float(payload["t_start"]), float(payload["t_end"])),
        float(payload["y0"]),
        n_points=int(payload.get("n_points", 200)),
        timeout=_budget(),
    )


def _execute_plot(payload: dict, computation_id: str) -> dict:
    svg: bytes = run_sandboxed(
        "plot_expression",
        payload["expression"],
        payload.get("variable", "x"),
        float(payload.get("x_min", -10.0)),
        float(payload.get("x_max", 10.0)),
        n_points=int(payload.get("n_points", 1000)),
        fmt="svg",
        timeout=_budget(),
    )
    ref = get_artifact_store().save(computation_id, "plot.svg", svg, "image/svg+xml")
    return {
        "artifact": ref.to_payload(),
        "artifact_url": f"/api/v1/computations/{computation_id}/artifact",
        "size_bytes": len(svg),
    }


def _execute_ml_classify(payload: dict, computation_id: str) -> dict:
    prediction = run_sandboxed("classify_pattern", payload["expression"], timeout=_budget())
    return {
        "label": str(prediction.label),
        "confidence": prediction.confidence,
        "source": prediction.source,
    }


_EXECUTORS = {
    "symbolic_solve": _execute_symbolic_solve,
    "integral": _execute_integral,
    "ode": _execute_ode,
    "plot": _execute_plot,
    "ml_classify": _execute_ml_classify,
}
