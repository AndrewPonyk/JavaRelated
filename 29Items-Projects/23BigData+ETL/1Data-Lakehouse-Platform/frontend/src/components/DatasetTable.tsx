import { useState } from "react";

import { useDatasets } from "../hooks/useDatasets";
import type { Dataset, DatasetLayer } from "../types/dataset";
import { DatasetForm } from "./DatasetForm";

const LAYERS: (DatasetLayer | "all")[] = ["all", "bronze", "silver", "gold"];

interface Props {
  onSelect: (dataset: Dataset) => void;
}

/** Catalog list: layer filter, registration form, loading/error/empty states. */
export function DatasetTable({ onSelect }: Props) {
  const [layerFilter, setLayerFilter] = useState<DatasetLayer | "all">("all");
  const [showForm, setShowForm] = useState(false);
  const { data, loading, error, refresh } = useDatasets(
    layerFilter === "all" ? undefined : layerFilter,
  );

  return (
    <section>
      <div className="toolbar">
        {LAYERS.map((layer) => (
          <button
            key={layer}
            className={`chip${layer === layerFilter ? " active" : ""}`}
            onClick={() => setLayerFilter(layer)}
          >
            {layer}
          </button>
        ))}
        <span className="spacer" />
        <button onClick={refresh} disabled={loading}>
          {loading ? "Loading…" : "Refresh"}
        </button>
        <button className="primary" onClick={() => setShowForm(true)}>
          New dataset
        </button>
      </div>

      {showForm && (
        <DatasetForm
          onCreated={() => {
            setShowForm(false);
            refresh();
          }}
          onClose={() => setShowForm(false)}
        />
      )}

      {error && (
        <div role="alert" className="error-banner">
          <strong>Could not load datasets.</strong> {error}{" "}
          <button onClick={refresh}>Retry</button>
        </div>
      )}

      {loading && !data && <p>Loading catalog…</p>}

      {!loading && !error && data && data.items.length === 0 && (
        <p>No datasets registered yet. Pipelines self-register tables on their first run.</p>
      )}

      {data && data.items.length > 0 && (
        <table className="data catalog">
          <thead>
            <tr>
              <th>Name</th>
              <th>Layer</th>
              <th>Owner</th>
              <th>Location</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((dataset) => (
              <tr key={dataset.id}>
                <td data-label="Name">
                  <button
                    className="link-button"
                    onClick={() => onSelect(dataset)}
                    title={dataset.description ?? undefined}
                  >
                    {dataset.name}
                  </button>
                </td>
                <td data-label="Layer">
                  <span className={`badge ${dataset.layer}`}>{dataset.layer}</span>
                </td>
                <td data-label="Owner">{dataset.owner_email}</td>
                <td data-label="Location" className="mono">
                  {dataset.s3_path}
                </td>
                <td data-label="Updated">{new Date(dataset.updated_at).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {data && (
        <p className="muted">
          {data.total} dataset{data.total === 1 ? "" : "s"}
        </p>
      )}
    </section>
  );
}
