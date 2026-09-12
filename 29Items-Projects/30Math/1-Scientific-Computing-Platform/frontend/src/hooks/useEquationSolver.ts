/**
 * Request state machine for equation solving.
 *
 * Pattern notes (this is the house style for data fetching):
 * - a discriminated union instead of separate isLoading/error/data flags —
 *   impossible states become unrepresentable;
 * - every new submission aborts the previous in-flight request;
 * - unmount aborts whatever is still running (no setState-after-unmount);
 * - a 202 from the backend (sync budget exceeded → background job) is a
 *   first-class state, not an error.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../api/client";
import { solveEquation } from "../api/symbolic";
import type { SolveQueuedResponse, SolveResponse } from "../types/api";

export type SolverState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; result: SolveResponse }
  | { status: "queued"; queued: SolveQueuedResponse }
  | { status: "error"; message: string };

export function useEquationSolver() {
  const [state, setState] = useState<SolverState>({ status: "idle" });
  const abortRef = useRef<AbortController | null>(null);

  const solve = useCallback(async (expression: string, variable = "x") => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setState({ status: "loading" });
    try {
      const outcome = await solveEquation({ expression, variable }, controller.signal);
      if (controller.signal.aborted) return;
      if (outcome.kind === "queued") {
        setState({ status: "queued", queued: outcome.queued });
      } else {
        setState({ status: "success", result: outcome.result });
      }
    } catch (error) {
      if (controller.signal.aborted) return; // superseded or unmounted — stay quiet
      const message =
        error instanceof ApiError
          ? error.message // problem+json detail is user-presentable by contract
          : "Could not reach the computation service. Please try again.";
      setState({ status: "error", message });
    }
  }, []);

  useEffect(() => () => abortRef.current?.abort(), []);

  return { state, solve };
}
