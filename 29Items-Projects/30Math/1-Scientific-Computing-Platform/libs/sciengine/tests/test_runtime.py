"""Sandbox contract tests: results cross the boundary, hangs get killed."""

import pytest

from sciengine.exceptions import (
    ComputationTimeoutError,
    ExpressionParseError,
    UnsupportedExpressionError,
)
from sciengine.runtime import SubprocessRunner


@pytest.fixture(scope="module")
def runner():
    r = SubprocessRunner()
    yield r
    r.close()


def test_runs_registered_operation(runner):
    result = runner.run("solve_equation", "x^2 - 4 = 0", "x", timeout=30)
    assert sorted(result.solutions) == ["-2", "2"]


def test_errors_cross_the_boundary_typed(runner):
    with pytest.raises(ExpressionParseError):
        runner.run("solve_equation", "x +* 2", "x", timeout=30)
    with pytest.raises(UnsupportedExpressionError):
        runner.run("solve_equation", "y + 1 = 0", "x", timeout=30)


def test_timeout_kills_and_recovers(runner):
    with pytest.raises(ComputationTimeoutError):
        runner.run("sleep_for_testing", 30.0, timeout=0.3)
    # The child was terminated; the next call must transparently respawn.
    result = runner.run("solve_equation", "x - 1 = 0", "x", timeout=60)
    assert result.solutions == ["1"]


def test_unknown_operation_rejected(runner):
    with pytest.raises(ValueError, match="Unknown sandbox operation"):
        runner.run("os_system", "id", timeout=1)


def test_close_is_idempotent_and_runner_respawns():
    r = SubprocessRunner()
    try:
        assert r.run("sleep_for_testing", 0.0, timeout=30) == "slept"
        r.close()
        r.close()  # second close: no-op
        # A closed runner lazily respawns on the next call.
        assert r.run("sleep_for_testing", 0.0, timeout=30) == "slept"
    finally:
        r.close()


def test_shared_runner_helpers():
    from sciengine.runtime import get_runner, run_sandboxed, shutdown_runner

    assert get_runner() is get_runner()  # process-wide singleton
    assert run_sandboxed("sleep_for_testing", 0.0, timeout=30) == "slept"
    shutdown_runner()
    # After shutdown a fresh singleton is created transparently.
    assert run_sandboxed("sleep_for_testing", 0.0, timeout=30) == "slept"
