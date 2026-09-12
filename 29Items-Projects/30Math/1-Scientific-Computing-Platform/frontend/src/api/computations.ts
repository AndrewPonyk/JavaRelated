/** Saved-computation endpoint wrappers (all authenticated). */

import type { ComputationKind, ComputationRead, Page } from "../types/api";
import { apiFetch, apiFetchBlob } from "./client";

export function listComputations(limit = 20, offset = 0): Promise<Page<ComputationRead>> {
  return apiFetch<Page<ComputationRead>>(`/api/v1/computations?limit=${limit}&offset=${offset}`);
}

export function getComputation(id: string): Promise<ComputationRead> {
  return apiFetch<ComputationRead>(`/api/v1/computations/${id}`);
}

export function createComputation(
  title: string,
  kind: ComputationKind,
  inputPayload: Record<string, unknown>,
): Promise<ComputationRead> {
  return apiFetch<ComputationRead>("/api/v1/computations", {
    method: "POST",
    body: JSON.stringify({ title, kind, input_payload: inputPayload }),
  });
}

export async function deleteComputation(id: string): Promise<void> {
  await apiFetch<void>(`/api/v1/computations/${id}`, { method: "DELETE" });
}

export function fetchComputationArtifact(id: string): Promise<Blob> {
  return apiFetchBlob(`/api/v1/computations/${id}/artifact`);
}
