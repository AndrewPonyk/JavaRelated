/** Register a device; surfaces the one-time API key. Operator/admin only. */

import { useState } from "react";
import { api } from "../api/client";
import { useChartTheme } from "../theme";

interface Props {
  onRegistered: () => void;
}

export function DeviceForm({ onRegistered }: Props) {
  const theme = useChartTheme();
  const [name, setName] = useState("");
  const [site, setSite] = useState("default");
  const [deviceType, setDeviceType] = useState("generic");
  const [nameError, setNameError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [issuedKey, setIssuedKey] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const inputStyle: React.CSSProperties = {
    display: "block",
    width: "100%",
    boxSizing: "border-box",
    padding: "6px 8px",
    marginBottom: 8,
    borderRadius: 6,
    border: `1px solid ${theme.gridline}`,
    background: theme.surface,
    color: theme.textPrimary,
    font: "inherit",
    fontSize: 13,
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (name.trim().length === 0) {
      setNameError("Name is required.");
      return;
    }
    setNameError(null);
    setError(null);
    setSubmitting(true);
    try {
      const created = await api.registerDevice({
        name: name.trim(),
        site: site.trim() || "default",
        device_type: deviceType.trim() || "generic",
      });
      setIssuedKey(created.api_key);
      setName("");
      onRegistered();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate style={{ marginTop: 16 }}>
      <h3 style={{ fontSize: 13, color: theme.textSecondary, margin: "0 0 8px" }}>
        Register device
      </h3>
      <input
        placeholder="Name *"
        value={name}
        onChange={(e) => setName(e.target.value)}
        aria-label="Device name"
        style={inputStyle}
      />
      {nameError && (
        <p role="alert" style={{ fontSize: 12, color: theme.statusCritical, margin: "0 0 8px" }}>
          ⚠ {nameError}
        </p>
      )}
      <input
        placeholder="Site"
        value={site}
        onChange={(e) => setSite(e.target.value)}
        aria-label="Site"
        style={inputStyle}
      />
      <input
        placeholder="Type"
        value={deviceType}
        onChange={(e) => setDeviceType(e.target.value)}
        aria-label="Device type"
        style={inputStyle}
      />
      <button
        type="submit"
        disabled={submitting}
        style={{
          padding: "6px 12px",
          borderRadius: 6,
          border: "none",
          background: theme.series1,
          color: "#ffffff",
          font: "inherit",
          fontSize: 13,
          cursor: "pointer",
        }}
      >
        {submitting ? "Registering…" : "Register"}
      </button>

      {error && (
        <p role="alert" style={{ fontSize: 12, color: theme.statusCritical }}>
          ⚠ {error}
        </p>
      )}

      {issuedKey && (
        <div
          style={{
            marginTop: 12,
            padding: 10,
            borderRadius: 6,
            border: `1px solid ${theme.series1}`,
            background: theme.surface,
            fontSize: 12,
          }}
        >
          <strong>Device API key — copy it now, it is shown only once:</strong>
          <code
            style={{
              display: "block",
              marginTop: 6,
              wordBreak: "break-all",
              color: theme.textSecondary,
            }}
          >
            {issuedKey}
          </code>
          <button
            type="button"
            onClick={() => setIssuedKey(null)}
            style={{
              marginTop: 8,
              padding: "4px 10px",
              borderRadius: 6,
              border: `1px solid ${theme.gridline}`,
              background: theme.surface,
              color: theme.textPrimary,
              font: "inherit",
              fontSize: 12,
              cursor: "pointer",
            }}
          >
            I copied it
          </button>
        </div>
      )}
    </form>
  );
}
