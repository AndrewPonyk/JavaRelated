// Studies dashboard: list studies, create a study, and open it to enrollment.
import { useState, type FormEvent } from "react";
import { AsyncBoundary } from "@/components/AsyncBoundary";
import { isReadOnly, useAuth } from "@/auth/AuthContext";
import { ApiError } from "@/api/client";
import {
  useAddArm,
  useCreateStudy,
  useOpenStudy,
  useProtocols,
  useStudies,
} from "./useTrials";
import type { Study } from "@/types";

const STATUS_LABEL: Record<Study["status"], string> = {
  DRAFT: "Draft",
  OPEN: "Open",
  PAUSED: "Paused",
  CLOSED: "Closed",
  COMPLETED: "Completed",
};

export function StudiesPage() {
  const { user } = useAuth();
  const readOnly = isReadOnly(user);
  const studies = useStudies();
  const protocols = useProtocols();
  const createStudy = useCreateStudy();
  const addArm = useAddArm();
  const openStudy = useOpenStudy();

  const [protocol, setProtocol] = useState("");
  const [name, setName] = useState("");
  const [target, setTarget] = useState(50);
  const [formError, setFormError] = useState<string | null>(null);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    if (!protocol) return setFormError("Select a protocol.");
    if (name.trim().length < 3) return setFormError("Name must be at least 3 characters.");
    try {
      await createStudy.mutateAsync({ protocol, name, target_enrollment: target });
      setName("");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Failed to create study.");
    }
  }

  async function onOpen(study: Study) {
    try {
      if (study.arms.length === 0) {
        await addArm.mutateAsync({ study: study.id, name: "Treatment" });
      }
      await openStudy.mutateAsync(study.id);
    } catch (err) {
      alert(err instanceof ApiError ? err.message : "Could not open study.");
    }
  }

  const rows = studies.data?.results ?? [];

  return (
    <section>
      <h2>Studies</h2>

      {!readOnly && (
        <form className="card" onSubmit={onCreate}>
          <h3>New study</h3>
          {formError && <div role="alert" className="error-box">{formError}</div>}
          <div className="form-row">
            <label>
              Protocol
              <select value={protocol} onChange={(e) => setProtocol(e.target.value)}>
                <option value="">Select…</option>
                {(protocols.data?.results ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.code} v{p.version} — {p.title}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Name
              <input value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <label>
              Target enrollment
              <input
                type="number"
                min={0}
                value={target}
                onChange={(e) => setTarget(Number(e.target.value))}
              />
            </label>
          </div>
          <button type="submit" disabled={createStudy.isPending}>
            {createStudy.isPending ? "Creating…" : "Create study"}
          </button>
        </form>
      )}

      <AsyncBoundary
        isLoading={studies.isLoading}
        isError={studies.isError}
        error={studies.error}
        isEmpty={rows.length === 0}
        emptyMessage="No studies yet."
        onRetry={() => studies.refetch()}
      >
        <table className="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th>Arms</th>
              <th>Target</th>
              {!readOnly && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {rows.map((study) => (
              <tr key={study.id}>
                <td>{study.name}</td>
                <td><span className={`badge status-${study.status}`}>{STATUS_LABEL[study.status]}</span></td>
                <td>{study.arms.length}</td>
                <td>{study.target_enrollment}</td>
                {!readOnly && (
                  <td>
                    {study.status === "DRAFT" && (
                      <button onClick={() => onOpen(study)} disabled={openStudy.isPending}>
                        Open
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </AsyncBoundary>
    </section>
  );
}
