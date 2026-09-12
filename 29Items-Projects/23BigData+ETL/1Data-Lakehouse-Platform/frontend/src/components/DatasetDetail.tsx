import { useState } from "react";

import { api, ApiError } from "../api/client";
import { useApiQuery } from "../hooks/useApiQuery";
import type {
  AuditEventList,
  Dataset,
  DatasetVersion,
  Preview,
} from "../types/dataset";

interface Props {
  datasetId: string;
  onBack: () => void;
}

export function DatasetDetail({ datasetId, onBack }: Props) {
  const dataset = useApiQuery<Dataset>(`/api/v1/datasets/${datasetId}`);
  const versions = useApiQuery<DatasetVersion[]>(`/api/v1/datasets/${datasetId}/versions`);
  const audit = useApiQuery<AuditEventList>(
    `/api/v1/audit?entity_type=dataset&entity_id=${datasetId}&limit=20`,
  );

  const [editing, setEditing] = useState(false);
  const [ownerDraft, setOwnerDraft] = useState("");
  const [descriptionDraft, setDescriptionDraft] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [preview, setPreview] = useState<Preview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  function startEditing() {
    if (!dataset.data) return;
    setOwnerDraft(dataset.data.owner_email);
    setDescriptionDraft(dataset.data.description ?? "");
    setActionError(null);
    setEditing(true);
  }

  async function saveEdits() {
    setSaving(true);
    setActionError(null);
    try {
      await api.patch<Dataset>(`/api/v1/datasets/${datasetId}`, {
        owner_email: ownerDraft,
        description: descriptionDraft.trim() ? descriptionDraft.trim() : null,
      });
      setEditing(false);
      dataset.refresh();
      audit.refresh();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Catalog API is unreachable");
    } finally {
      setSaving(false);
    }
  }

  async function deleteDataset() {
    if (!dataset.data) return;
    const confirmed = window.confirm(
      `Delete catalog entry '${dataset.data.name}'? The underlying Delta table is NOT touched.`,
    );
    if (!confirmed) return;
    try {
      await api.delete(`/api/v1/datasets/${datasetId}`);
      onBack();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Catalog API is unreachable");
    }
  }

  async function loadPreview() {
    setPreviewLoading(true);
    setPreviewError(null);
    try {
      setPreview(await api.get<Preview>(`/api/v1/datasets/${datasetId}/preview?limit=10`));
    } catch (err) {
      setPreview(null);
      setPreviewError(err instanceof ApiError ? err.message : "Catalog API is unreachable");
    } finally {
      setPreviewLoading(false);
    }
  }

  if (dataset.loading) return <p>Loading dataset…</p>;
  if (dataset.error || !dataset.data) {
    return (
      <div role="alert" className="error-banner">
        {dataset.error ?? "Dataset not found"} <button onClick={onBack}>Back</button>
      </div>
    );
  }
  const item = dataset.data;

  return (
    <section>
      <div className="toolbar">
        <button onClick={onBack}>← All datasets</button>
        <span className="spacer" />
        {!editing && <button onClick={startEditing}>Edit</button>}
        <button className="danger" onClick={deleteDataset}>
          Delete
        </button>
      </div>

      {actionError && (
        <div role="alert" className="error-banner">
          {actionError}
        </div>
      )}

      <div className="panel">
        <h2>
          {item.name} <span className={`badge ${item.layer}`}>{item.layer}</span>
        </h2>
        {!editing ? (
          <dl className="meta">
            <dt>Owner</dt>
            <dd>{item.owner_email}</dd>
            <dt>Location</dt>
            <dd className="mono">{item.s3_path}</dd>
            <dt>Description</dt>
            <dd>{item.description ?? <span className="muted">—</span>}</dd>
            <dt>Created</dt>
            <dd>{new Date(item.created_at).toLocaleString()}</dd>
            <dt>Updated</dt>
            <dd>{new Date(item.updated_at).toLocaleString()}</dd>
          </dl>
        ) : (
          <form
            className="dataset-form"
            onSubmit={(e) => {
              e.preventDefault();
              void saveEdits();
            }}
          >
            <div>
              <label htmlFor="edit-owner">Owner email</label>
              <input
                id="edit-owner"
                value={ownerDraft}
                onChange={(e) => setOwnerDraft(e.target.value)}
              />
            </div>
            <div className="full">
              <label htmlFor="edit-desc">Description</label>
              <textarea
                id="edit-desc"
                rows={2}
                value={descriptionDraft}
                onChange={(e) => setDescriptionDraft(e.target.value)}
              />
            </div>
            <div className="actions">
              <button type="button" onClick={() => setEditing(false)}>
                Cancel
              </button>
              <button type="submit" className="primary" disabled={saving}>
                {saving ? "Saving…" : "Save"}
              </button>
            </div>
          </form>
        )}
      </div>

      <div className="detail-grid">
        <div className="panel">
          <h3>Schema versions</h3>
          {versions.loading && <p>Loading…</p>}
          {versions.error && <p className="muted">{versions.error}</p>}
          {versions.data && versions.data.length === 0 && (
            <p className="muted">No versions registered yet — pipelines add one per run.</p>
          )}
          {versions.data && versions.data.length > 0 && (
            <table className="data">
              <thead>
                <tr>
                  <th>v</th>
                  <th>Columns</th>
                  <th>Rows</th>
                  <th>Registered</th>
                </tr>
              </thead>
              <tbody>
                {versions.data.map((version) => (
                  <tr key={version.id}>
                    <td>{version.version}</td>
                    <td className="mono">{Object.keys(version.schema_json).join(", ")}</td>
                    <td>{version.row_count ?? "—"}</td>
                    <td>{new Date(version.created_at).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="panel">
          <h3>Audit trail</h3>
          {audit.loading && <p>Loading…</p>}
          {audit.error && <p className="muted">{audit.error}</p>}
          {audit.data && audit.data.items.length === 0 && (
            <p className="muted">No audit events.</p>
          )}
          {audit.data && audit.data.items.length > 0 && (
            <table className="data">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Actor</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {audit.data.items.map((event) => (
                  <tr key={event.id}>
                    <td>{new Date(event.occurred_at).toLocaleString()}</td>
                    <td>{event.actor}</td>
                    <td className="mono">{event.action}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <div className="panel">
        <div className="toolbar">
          <h3 style={{ margin: 0 }}>Data preview</h3>
          <span className="spacer" />
          <button onClick={loadPreview} disabled={previewLoading}>
            {previewLoading ? "Querying Trino…" : "Load preview"}
          </button>
        </div>
        {previewError && (
          <div role="alert" className="error-banner">
            {previewError}
          </div>
        )}
        {preview && (
          <div className="preview-scroll">
            <p className="muted">
              {preview.row_count} rows from <span className="mono">{preview.source}</span>
            </p>
            <table className="data">
              <thead>
                <tr>
                  {preview.columns.map((column) => (
                    <th key={column}>{column}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((row, index) => (
                  <tr key={index}>
                    {row.map((value, cell) => (
                      <td key={cell}>{value === null ? "∅" : String(value)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!preview && !previewError && (
          <p className="muted">
            Read-only, LIMIT-capped sample served via Trino. Requires the Trino catalog to be
            reachable from the API.
          </p>
        )}
      </div>
    </section>
  );
}
