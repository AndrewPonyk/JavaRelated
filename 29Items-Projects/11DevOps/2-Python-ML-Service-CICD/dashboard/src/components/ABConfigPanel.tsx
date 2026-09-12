import { useEffect, useState } from "react";

import { ApiError, api } from "../api/client";
import type { ABConfig } from "../api/types";

interface Props {
  config: ABConfig | null;
  loading: boolean;
  error: string | null;
  onChanged: () => void;
}

/** Runtime control of the champion/challenger experiment. */
export default function ABConfigPanel({ config, loading, error, onChanged }: Props) {
  const [enabled, setEnabled] = useState(true);
  const [split, setSplit] = useState("10");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (config) {
      setEnabled(config.enabled);
      setSplit(String(config.traffic_split));
    }
  }, [config]);

  function validate(value: string): number | null {
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed < 0 || parsed > 100) {
      setFieldError("Traffic split must be an integer between 0 and 100.");
      return null;
    }
    setFieldError(null);
    return parsed;
  }

  async function save() {
    const parsed = validate(split);
    if (parsed === null) {
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      await api.updateABConfig({ enabled, traffic_split: parsed });
      onChanged();
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.detail : String(err));
      // Roll the inputs back to the last known good server state.
      if (config) {
        setEnabled(config.enabled);
        setSplit(String(config.traffic_split));
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card" aria-busy={loading}>
      <header className="card-header">
        <h2>A/B experiment</h2>
        {config && (
          <span className="muted">
            champion v{config.champion_version ?? "—"} vs challenger v
            {config.challenger_version ?? "—"}
          </span>
        )}
      </header>

      {error && <p className="error-banner">{error}</p>}
      {loading && !config && <div className="skeleton" />}

      {config && (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <div className="form-row">
            <label htmlFor="ab-enabled">Experiment enabled</label>
            <input
              id="ab-enabled"
              type="checkbox"
              checked={enabled}
              onChange={(event) => setEnabled(event.target.checked)}
            />
          </div>
          <div className="form-row">
            <label htmlFor="ab-split">Challenger traffic %</label>
            <input
              id="ab-split"
              type="number"
              min={0}
              max={100}
              value={split}
              onChange={(event) => {
                setSplit(event.target.value);
                validate(event.target.value);
              }}
            />
          </div>
          {fieldError && <p className="field-error">{fieldError}</p>}
          {saveError && <p className="error-banner">Save failed: {saveError}</p>}
          <button type="submit" disabled={saving || fieldError !== null}>
            {saving ? "Saving…" : "Save"}
          </button>
        </form>
      )}
    </section>
  );
}
