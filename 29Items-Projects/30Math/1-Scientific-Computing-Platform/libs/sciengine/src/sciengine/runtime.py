"""Process-isolated execution of potentially unbounded computations.

SymPy can hang — not just run slowly — and neither thread timeouts nor
``signal.SIGALRM`` (absent on Windows) can stop it. The only reliable guard is
a separate process that the caller can kill (docs/ARCHITECTURE.md §2.4). This
module provides that guard for both the API fast path and the Celery workers.

Design:

- one long-lived child process per :class:`SubprocessRunner` (spawn context —
  fork is unsafe with BLAS threads and unavailable on Windows);
- the child imports SymPy once at startup ("warm-up"), so per-call budgets
  measure the computation, not interpreter start-up;
- only operations in :data:`OPERATIONS` can execute in the child — callers
  pass names + JSON-ish arguments, never callables;
- on timeout the child is terminated and respawned lazily; errors cross the
  boundary as ``(code, message)`` pairs and are re-raised as the matching
  :mod:`sciengine.exceptions` type.
"""

from __future__ import annotations

import contextlib
import importlib
import multiprocessing as mp
import threading
import time
from typing import Any

from sciengine.exceptions import (
    ComputationError,
    ComputationTimeoutError,
    ConvergenceError,
    ExpressionParseError,
    SciEngineError,
    UnsupportedExpressionError,
)

#: Operations the sandbox may execute, as "module:attribute" specs. This is a
#: closed set on purpose: arbitrary callables must never cross the boundary.
OPERATIONS: dict[str, str] = {
    "solve_equation": "sciengine.symbolic.solver:solve_equation",
    "solve_system": "sciengine.symbolic.solver:solve_system",
    "differentiate": "sciengine.symbolic.calculus:differentiate",
    "integrate_symbolic": "sciengine.symbolic.calculus:integrate_symbolic",
    "limit": "sciengine.symbolic.calculus:limit",
    "taylor_series": "sciengine.symbolic.calculus:taylor_series",
    "classify_pattern": "sciengine.ml.features:classify_pattern",
    "plot_expression": "sciengine.plotting.function_plot:plot_sympy_expression",
    "solve_ivp_expression": "sciengine.numerical.ode:solve_ivp_expression",
    "warmup": "sciengine.runtime:_warmup",
    "sleep_for_testing": "sciengine.runtime:_sleep_for_testing",
}

_WARMUP_TIMEOUT_SECONDS = 120.0  # spawn + numpy/sympy/matplotlib imports

_ERROR_CLASSES: tuple[type[SciEngineError], ...] = (
    SciEngineError,
    ExpressionParseError,
    UnsupportedExpressionError,
    ComputationError,
    ConvergenceError,
    ComputationTimeoutError,
)
_ERROR_BY_CODE: dict[str, type[SciEngineError]] = {cls.code: cls for cls in _ERROR_CLASSES}


def _warmup() -> str:
    """Pre-import the heavy scientific stack inside the child."""
    import sciengine.ml.features
    import sciengine.symbolic.calculus
    import sciengine.symbolic.solver  # noqa: F401

    return "ready"


def _sleep_for_testing(seconds: float) -> str:
    """Deterministically slow operation used by timeout tests (not exposed by any API)."""
    time.sleep(seconds)
    return "slept"


def _resolve(spec: str) -> Any:
    module_name, _, attribute = spec.partition(":")
    return getattr(importlib.import_module(module_name), attribute)


def _child_main(conn: Any) -> None:  # pragma: no cover - runs in the child process
    resolved: dict[str, Any] = {}
    while True:
        try:
            message = conn.recv()
        except (EOFError, KeyboardInterrupt):
            return
        if message is None:
            return
        op, args, kwargs = message
        try:
            fn = resolved.get(op)
            if fn is None:
                fn = resolved[op] = _resolve(OPERATIONS[op])
            conn.send(("ok", fn(*args, **kwargs)))
        except SciEngineError as exc:
            conn.send(("error", exc.code, str(exc)))
        except Exception as exc:  # - boundary: everything becomes a code
            conn.send(("error", "computation_error", f"{type(exc).__name__}: {exc}"))


class SubprocessRunner:
    """A killable sandbox process with per-call timeouts. Thread-safe."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._ctx = mp.get_context("spawn")
        self._process: Any = None
        self._conn: Any = None

    # -- lifecycle -----------------------------------------------------------

    def _ensure_child(self) -> None:
        if self._process is not None and self._process.is_alive():
            return
        parent_conn, child_conn = self._ctx.Pipe()
        process = self._ctx.Process(target=_child_main, args=(child_conn,), daemon=True)
        process.start()
        child_conn.close()
        self._process, self._conn = process, parent_conn
        # Warm-up outside any caller's budget so budgets measure math, not imports.
        self._conn.send(("warmup", (), {}))
        if not self._conn.poll(_WARMUP_TIMEOUT_SECONDS):
            self._kill_child()
            raise ComputationError("Sandbox process failed to start within the warm-up window.")
        status, *payload = self._conn.recv()
        if status != "ok":
            self._kill_child()
            raise ComputationError(f"Sandbox warm-up failed: {payload}")

    def _kill_child(self) -> None:
        if self._process is not None:
            self._process.terminate()
            self._process.join(timeout=5)
        if self._conn is not None:
            self._conn.close()
        self._process = self._conn = None

    def close(self) -> None:
        with self._lock:
            if self._conn is not None:
                with contextlib.suppress(OSError):
                    self._conn.send(None)
            self._kill_child()

    # -- execution -----------------------------------------------------------

    def run(self, op: str, /, *args: Any, timeout: float, **kwargs: Any) -> Any:
        """Run a registered operation with a hard timeout (seconds).

        Raises :class:`ComputationTimeoutError` after killing the child on
        budget overrun; re-raises sciengine errors from inside the sandbox.
        """
        if op not in OPERATIONS:
            raise ValueError(f"Unknown sandbox operation: {op!r}")
        with self._lock:
            self._ensure_child()
            try:
                self._conn.send((op, args, kwargs))
                if not self._conn.poll(timeout):
                    self._kill_child()
                    raise ComputationTimeoutError(
                        f"Operation '{op}' exceeded its {timeout:.3g}s budget and was terminated."
                    )
                status, *payload = self._conn.recv()
            except (BrokenPipeError, EOFError, OSError) as exc:
                self._kill_child()
                raise ComputationError(f"Sandbox process failed mid-operation: {exc}") from exc
        if status == "ok":
            return payload[0]
        code, message = payload
        raise _ERROR_BY_CODE.get(code, SciEngineError)(message)


_default_runner: SubprocessRunner | None = None
_default_runner_lock = threading.Lock()


def get_runner() -> SubprocessRunner:
    """Shared process-wide runner (one warm child per API pod / worker process)."""
    global _default_runner
    with _default_runner_lock:
        if _default_runner is None:
            _default_runner = SubprocessRunner()
        return _default_runner


def run_sandboxed(op: str, /, *args: Any, timeout: float, **kwargs: Any) -> Any:
    """Convenience wrapper over the shared runner."""
    return get_runner().run(op, *args, timeout=timeout, **kwargs)


def shutdown_runner() -> None:
    """Close the shared runner (app shutdown hooks / test teardown)."""
    global _default_runner
    with _default_runner_lock:
        if _default_runner is not None:
            _default_runner.close()
            _default_runner = None
