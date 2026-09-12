/**
 * Saved computations: submit background jobs, watch them reach a terminal
 * state (polling while anything is queued/running), inspect results, view
 * plot artifacts, delete.
 */

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../api/client";
import {
  createComputation,
  deleteComputation,
  fetchComputationArtifact,
  listComputations,
} from "../api/computations";
import type { ComputationKind, ComputationRead } from "../types/api";
import { LatexBlock } from "./LatexBlock";

const KIND_OPTIONS: { value: ComputationKind; label: string }[] = [
  { value: "symbolic_solve", label: "Solve equation" },
  { value: "integral", label: "Indefinite integral" },
  { value: "ode", label: "ODE  dy/dt = f(t, y)" },
  { value: "plot", label: "Plot function" },
  { value: "ml_classify", label: "Classify pattern" },
];

const POLL_INTERVAL_MS = 1500;

function buildPayload(kind: ComputationKind, expression: string): Record<string, unknown> {
  switch (kind) {
    case "ode":
      return { expression, t_start: 0, t_end: 10, y0: 1 };
    case "plot":
      return { expression, x_min: -10, x_max: 10 };
    default:
      return { expression, variable: "x" };
  }
}

export function ComputationsPage() {
  const [items, setItems] = useState<ComputationRead[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [kind, setKind] = useState<ComputationKind>("symbolic_solve");
  const [expression, setExpression] = useState("x^3 - 6x^2 + 11x - 6 = 0");
  const [submitting, setSubmitting] = useState(false);
  const [artifactUrls, setArtifactUrls] = useState<Record<string, string>>({});
  const artifactUrlsRef = useRef(artifactUrls);
  artifactUrlsRef.current = artifactUrls;

  const refresh = useCallback(async () => {
    try {
      const page = await listComputations(50, 0);
      setItems(page.items);
      setTotal(page.total);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load computations.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Poll while any job is still in flight.
  const hasPending = items.some((c) => c.status === "queued" || c.status === "running");
  useEffect(() => {
    if (!hasPending) return;
    const timer = setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [hasPending, refresh]);

  // Revoke artifact object URLs on unmount.
  useEffect(
    () => () => {
      Object.values(artifactUrlsRef.current).forEach((url) => URL.revokeObjectURL(url));
    },
    [],
  );

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!expression.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const label = KIND_OPTIONS.find((option) => option.value === kind)?.label ?? kind;
      await createComputation(
        `${label}: ${expression.slice(0, 60)}`,
        kind,
        buildPayload(kind, expression),
      );
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not submit the computation.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteComputation(id);
      setItems((current) => current.filter((c) => c.id !== id));
      setTotal((current) => Math.max(0, current - 1));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Delete failed.");
    }
  };

  const handleViewArtifact = async (id: string) => {
    try {
      const blob = await fetchComputationArtifact(id);
      setArtifactUrls((current) => ({ ...current, [id]: URL.createObjectURL(blob) }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load the plot.");
    }
  };

  return (
    <section className="card" aria-label="My computations">
      <h2>My computations</h2>

      <form onSubmit={(e) => void handleSubmit(e)} className="solver-form">
        <label htmlFor="comp-kind">Operation</label>
        <select
          id="comp-kind"
          value={kind}
          onChange={(e) => setKind(e.target.value as ComputationKind)}
        >
          {KIND_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>

        <label htmlFor="comp-expression">Expression</label>
        <input
          id="comp-expression"
          value={expression}
          onChange={(e) => setExpression(e.target.value)}
          maxLength={512}
          autoComplete="off"
          required
        />

        <button type="submit" disabled={submitting}>
          {submitting ? "Submitting…" : "Run in background"}
        </button>
      </form>

      {error && (
        <p role="alert" className="error-banner">
          {error}
        </p>
      )}
      {loading && (
        <p role="status" className="muted">
          Loading…
        </p>
      )}

      {!loading && items.length === 0 && <p className="muted">No computations yet.</p>}

      {items.length > 0 && (
        <>
          <p className="muted">
            {total} total{hasPending ? " — refreshing while jobs run…" : ""}
          </p>
          <ul className="computation-list" aria-label="Computation list">
            {items.map((computation) => (
              <li key={computation.id} className="computation-item">
                <div className="computation-header">
                  <span className={`status status-${computation.status}`}>
                    {computation.status}
                  </span>
                  <strong>{computation.title}</strong>
                  <button
                    type="button"
                    className="link-button danger"
                    onClick={() => void handleDelete(computation.id)}
                    aria-label={`Delete ${computation.title}`}
                  >
                    Delete
                  </button>
                </div>
                <ComputationResult
                  computation={computation}
                  artifactUrl={artifactUrls[computation.id]}
                  onViewArtifact={() => void handleViewArtifact(computation.id)}
                />
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

function ComputationResult({
  computation,
  artifactUrl,
  onViewArtifact,
}: {
  computation: ComputationRead;
  artifactUrl?: string;
  onViewArtifact: () => void;
}) {
  const result = computation.result_payload;
  if (computation.status === "failed") {
    return (
      <p className="error-banner">
        Failed ({computation.error_code}): {String(result?.error ?? "no detail")}
      </p>
    );
  }
  if (computation.status !== "succeeded" || !result) {
    return <p className="muted">Working…</p>;
  }
  switch (computation.kind) {
    case "symbolic_solve": {
      const latex = (result.solutions_latex as string[] | undefined) ?? [];
      return latex.length === 0 ? (
        <p className="muted">No solutions.</p>
      ) : (
        <p>
          {latex.map((solution, i) => (
            <span key={i} className="solution-chip">
              <LatexBlock latex={`x = ${solution}`} />
            </span>
          ))}
        </p>
      );
    }
    case "integral":
      return <LatexBlock latex={String(result.result_latex ?? "")} displayMode />;
    case "ode": {
      const y = (result.y as number[] | undefined) ?? [];
      return (
        <p className="muted">
          {y.length} samples · y(end) ≈ {y.length ? y[y.length - 1].toFixed(6) : "—"} ·{" "}
          {String(result.method)}
        </p>
      );
    }
    case "plot":
      return artifactUrl ? (
        <img src={artifactUrl} alt={computation.title} className="plot-image" />
      ) : (
        <button type="button" className="link-button" onClick={onViewArtifact}>
          View plot
        </button>
      );
    case "ml_classify":
      return (
        <p className="muted">
          {String(result.label)} · confidence {Number(result.confidence).toFixed(2)} ·{" "}
          {String(result.source)}
        </p>
      );
    default:
      return <p className="muted">Done.</p>;
  }
}
