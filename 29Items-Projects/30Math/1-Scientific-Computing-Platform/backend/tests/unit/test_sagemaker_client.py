"""ML classification client: fail-soft contract (docs/ARCHITECTURE.md §2.6)."""

from app.services import sagemaker_client
from sciengine.ml.features import PatternLabel, PatternPrediction


def test_no_endpoint_configured_uses_heuristic(app_env):
    prediction = sagemaker_client.classify_equation("x^2 + 3x")
    assert prediction.label == PatternLabel.QUADRATIC
    assert prediction.source == "heuristic"


def test_sagemaker_failure_falls_back_to_heuristic(app_env, monkeypatch):
    monkeypatch.setenv("SAGEMAKER_RECOGNIZER_ENDPOINT", "scp-recognizer")
    from tests.conftest import _clear_runtime_caches

    _clear_runtime_caches()

    def boom(expression: str) -> PatternPrediction:
        raise RuntimeError("endpoint unreachable")

    monkeypatch.setattr(sagemaker_client, "_invoke_sagemaker", boom)
    prediction = sagemaker_client.classify_equation("sin(x)")
    assert prediction.label == PatternLabel.TRIGONOMETRIC
    assert prediction.source == "heuristic"  # the truth field


def test_sagemaker_success_passes_through(app_env, monkeypatch):
    monkeypatch.setenv("SAGEMAKER_RECOGNIZER_ENDPOINT", "scp-recognizer")
    from tests.conftest import _clear_runtime_caches

    _clear_runtime_caches()

    def fake_invoke(expression: str) -> PatternPrediction:
        return PatternPrediction(PatternLabel.LINEAR, 0.99, "sagemaker")

    monkeypatch.setattr(sagemaker_client, "_invoke_sagemaker", fake_invoke)
    prediction = sagemaker_client.classify_equation("2x + 1")
    assert prediction.source == "sagemaker"
    assert prediction.confidence == 0.99
