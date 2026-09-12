/** Endpoint wrappers for the symbolic + plot APIs. */

import type { SolveOutcome, SolveQueuedResponse, SolveRequest, SolveResponse } from "../types/api";
import { apiFetchBlob, apiRequest } from "./client";

export async function solveEquation(
  request: SolveRequest,
  signal?: AbortSignal,
): Promise<SolveOutcome> {
  const { status, data } = await apiRequest<SolveResponse | SolveQueuedResponse>(
    "/api/v1/symbolic/solve",
    { method: "POST", body: JSON.stringify(request), signal },
  );
  if (status === 202) {
    return { kind: "queued", queued: data as SolveQueuedResponse };
  }
  return { kind: "result", result: data as SolveResponse };
}

export function renderFunctionPlot(
  expression: string,
  variable = "x",
  xMin = -10,
  xMax = 10,
  signal?: AbortSignal,
): Promise<Blob> {
  return apiFetchBlob("/api/v1/plots/function", {
    method: "POST",
    body: JSON.stringify({ expression, variable, x_min: xMin, x_max: xMax }),
    signal,
  });
}
