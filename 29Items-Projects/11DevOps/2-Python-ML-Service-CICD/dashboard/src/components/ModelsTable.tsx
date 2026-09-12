import { useState } from "react";

import { ApiError, api } from "../api/client";
import type { ModelListResponse } from "../api/types";

interface Props {
  data: ModelListResponse | null;
  loading: boolean;
  error: string | null;
  onChanged: () => void;
}

/** Registered model versions with alias badges and a promote action. */
export default function ModelsTable({ data, loading, error, onChanged }: Props) {
  const [busyVersion, setBusyVersion] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function promote(version: string) {
    if (!window.confirm(`Promote version ${version} to champion?`)) {
      return;
    }
    setBusyVersion(version);
    setActionError(null);
    try {
      await api.promote(version, "champion");
      onChanged();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.detail : String(err));
    } finally {
      setBusyVersion(null);
    }
  }

  return (
    <section className="card" aria-busy={loading}>
      <header className="card-header">
        <h2>Model versions</h2>
        {data && (
          <span className="muted">
            champion v{data.aliases.champion ?? "—"} · challenger v
            {data.aliases.challenger ?? "—"}
          </span>
        )}
      </header>

      {error && <p className="error-banner">{error}</p>}
      {actionError && <p className="error-banner">Promotion failed: {actionError}</p>}
      {loading && !data && <div className="skeleton" />}
      {data && data.items.length === 0 && (
        <p className="muted">No models registered yet — run a training first.</p>
      )}

      {data && data.items.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Version</th>
                <th>Stage</th>
                <th>AUC</th>
                <th>Registered</th>
                <th>Source</th>
                <th aria-label="actions" />
              </tr>
            </thead>
            <tbody>
              {data.items.map((item) => (
                <tr key={`${item.source}-${item.version}`}>
                  <td>v{item.version}</td>
                  <td>
                    <span className={`badge ${item.stage}`}>{item.stage}</span>
                  </td>
                  <td>{item.auc == null ? "—" : item.auc.toFixed(4)}</td>
                  <td>
                    {item.registered_at
                      ? new Date(item.registered_at).toLocaleString()
                      : "—"}
                  </td>
                  <td>{item.source}</td>
                  <td>
                    {item.version !== data.aliases.champion && (
                      <button
                        type="button"
                        disabled={busyVersion !== null}
                        onClick={() => void promote(item.version)}
                      >
                        {busyVersion === item.version ? "Promoting…" : "Promote"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
