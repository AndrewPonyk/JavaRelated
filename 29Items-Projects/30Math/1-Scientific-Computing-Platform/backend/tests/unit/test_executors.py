"""Executor dispatch: each kind produces a JSON-safe result payload."""

import json

import pytest

from app.workers.executors import execute_computation


def _assert_json_safe(payload: dict) -> None:
    json.dumps(payload)  # raises if anything non-serializable leaked through


def test_symbolic_solve(app_env):
    result = execute_computation(
        "symbolic_solve", {"expression": "x^2 - 1 = 0", "variable": "x"}, "cid-1"
    )
    assert sorted(result["solutions"]) == ["-1", "1"]
    _assert_json_safe(result)


def test_integral(app_env):
    result = execute_computation("integral", {"expression": "3x^2", "variable": "x"}, "cid-2")
    assert result["result_text"] == "x**3"
    _assert_json_safe(result)


def test_ode(app_env):
    result = execute_computation(
        "ode",
        {"expression": "-y", "t_start": 0.0, "t_end": 1.0, "y0": 1.0, "n_points": 25},
        "cid-3",
    )
    assert result["success"] is True
    assert len(result["t"]) == 25
    _assert_json_safe(result)


def test_plot_saves_artifact(app_env):
    result = execute_computation(
        "plot", {"expression": "cos(x)", "variable": "x", "x_min": -3.0, "x_max": 3.0}, "cid-4"
    )
    assert result["artifact"]["storage"] == "local"
    assert result["size_bytes"] > 500
    _assert_json_safe(result)

    from app.services.artifact_store import ArtifactRef, get_artifact_store

    data = get_artifact_store().read(ArtifactRef.from_payload(result["artifact"]))
    assert b"<svg" in data


def test_ml_classify(app_env):
    result = execute_computation("ml_classify", {"expression": "exp(3x)"}, "cid-5")
    assert result["label"] == "exponential"
    _assert_json_safe(result)


def test_unknown_kind_rejected(app_env):
    with pytest.raises(ValueError, match="Unknown computation kind"):
        execute_computation("mining", {}, "cid-6")
