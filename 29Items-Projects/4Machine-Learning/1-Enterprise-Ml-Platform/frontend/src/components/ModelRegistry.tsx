// Model Registry browser — demonstrates the standard data-fetching pattern:
// loading, error, empty, and success states, plus a mutation (promote).
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { api, ApiError } from "../api/client";
import { useModelVersions } from "../hooks/useModelVersions";
import type { ModelStage, ModelVersion } from "../types";

interface Props {
  modelName: string;
}

export function ModelRegistry({ modelName }: Props): JSX.Element {
  const { data, isLoading, isError, error } = useModelVersions(modelName);
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<number | null>(null);

  const promote = useMutation({
    mutationFn: (version: number) =>
      api.promoteModel(modelName, version, "Production" satisfies ModelStage),
    onMutate: (version) => setPending(version),
    onSettled: () => {
      setPending(null);
      void queryClient.invalidateQueries({ queryKey: ["model-versions", modelName] });
    },
  });

  // --- Loading state ---------------------------------------------------------
  if (isLoading) {
    return <p role="status">Loading versions for “{modelName}”…</p>;
  }

  // --- Error state -----------------------------------------------------------
  if (isError) {
    const message = error instanceof ApiError ? error.message : "Unknown error";
    return (
      <p role="alert" className="error">
        Failed to load model versions: {message}
      </p>
    );
  }

  // --- Empty state -----------------------------------------------------------
  if (!data || data.length === 0) {
    return <p>No registered versions for “{modelName}” yet.</p>;
  }

  // --- Success state ---------------------------------------------------------
  return (
    <table className="model-registry">
      <thead>
        <tr>
          <th>Version</th>
          <th>Stage</th>
          <th>Framework</th>
          <th>Run</th>
          <th aria-label="actions" />
        </tr>
      </thead>
      <tbody>
        {data.map((mv: ModelVersion) => (
          <tr key={mv.version}>
            <td>{mv.version}</td>
            <td>
              <span className={`badge badge--${mv.stage.toLowerCase()}`}>{mv.stage}</span>
            </td>
            <td>{mv.framework}</td>
            <td>
              <code>{mv.runId.slice(0, 8)}</code>
            </td>
            <td>
              <button
                disabled={mv.stage === "Production" || pending === mv.version}
                onClick={() => promote.mutate(mv.version)}
              >
                {pending === mv.version ? "Promoting…" : "Promote to Prod"}
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
