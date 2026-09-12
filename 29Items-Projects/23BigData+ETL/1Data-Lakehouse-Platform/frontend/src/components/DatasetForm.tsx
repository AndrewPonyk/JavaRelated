import { type FormEvent, useState } from "react";

import { api, ApiError } from "../api/client";
import type { Dataset, DatasetCreatePayload, DatasetLayer } from "../types/dataset";

// Mirrors the server-side rules in app/schemas/dataset.py: instant feedback
// here, the API remains authoritative.
const NAME_RE = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$/;
const NAME_MIN = 3;
const NAME_MAX = 120;
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

interface Props {
  onCreated: (dataset: Dataset) => void;
  onClose: () => void;
}

type FieldErrors = Partial<Record<"name" | "owner_email" | "s3_path", string>>;

export function DatasetForm({ onCreated, onClose }: Props) {
  const [name, setName] = useState("");
  const [layer, setLayer] = useState<DatasetLayer>("bronze");
  const [ownerEmail, setOwnerEmail] = useState("");
  const [s3Path, setS3Path] = useState("");
  const [description, setDescription] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (name.length < NAME_MIN || name.length > NAME_MAX || !NAME_RE.test(name)) {
      errors.name =
        "Dot-separated lowercase identifiers (each starting with a letter), " +
        "3–120 chars, e.g. sales.orders";
    }
    if (!EMAIL_RE.test(ownerEmail)) {
      errors.owner_email = "Must be a valid email address";
    }
    if (!/^(s3|s3a|file):\/\//.test(s3Path)) {
      errors.s3_path = "Must start with s3://, s3a:// or file://";
    }
    return errors;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const errors = validate();
    setFieldErrors(errors);
    setSubmitError(null);
    if (Object.keys(errors).length > 0) return;

    const payload: DatasetCreatePayload = {
      name,
      layer,
      owner_email: ownerEmail,
      s3_path: s3Path,
      description: description.trim() ? description.trim() : null,
    };

    setSubmitting(true);
    try {
      const created = await api.post<Dataset>("/api/v1/datasets", payload);
      onCreated(created);
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : "Catalog API is unreachable");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="panel">
      <h2>Register dataset</h2>
      {submitError && (
        <div role="alert" className="error-banner">
          {submitError}
        </div>
      )}
      <form className="dataset-form" onSubmit={handleSubmit} noValidate>
        <div>
          <label htmlFor="ds-name">Name</label>
          <input
            id="ds-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="sales.orders"
            maxLength={NAME_MAX}
          />
          {fieldErrors.name && <div className="field-error">{fieldErrors.name}</div>}
        </div>
        <div>
          <label htmlFor="ds-layer">Layer</label>
          <select
            id="ds-layer"
            value={layer}
            onChange={(e) => setLayer(e.target.value as DatasetLayer)}
          >
            <option value="bronze">bronze</option>
            <option value="silver">silver</option>
            <option value="gold">gold</option>
          </select>
        </div>
        <div>
          <label htmlFor="ds-owner">Owner email</label>
          <input
            id="ds-owner"
            value={ownerEmail}
            onChange={(e) => setOwnerEmail(e.target.value)}
            placeholder="team@example.com"
            maxLength={254}
          />
          {fieldErrors.owner_email && (
            <div className="field-error">{fieldErrors.owner_email}</div>
          )}
        </div>
        <div>
          <label htmlFor="ds-path">S3 location</label>
          <input
            id="ds-path"
            value={s3Path}
            onChange={(e) => setS3Path(e.target.value)}
            placeholder="s3://dev-lakehouse-silver/sales/orders"
            maxLength={1024}
          />
          {fieldErrors.s3_path && <div className="field-error">{fieldErrors.s3_path}</div>}
        </div>
        <div className="full">
          <label htmlFor="ds-desc">Description (optional)</label>
          <textarea
            id="ds-desc"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={2000}
          />
        </div>
        <div className="actions">
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="primary" disabled={submitting}>
            {submitting ? "Registering…" : "Register"}
          </button>
        </div>
      </form>
    </div>
  );
}
