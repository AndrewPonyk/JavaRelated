/** Sign-in form: credentials → JWT via /auth/token. */

import { useState } from "react";
import { api } from "../api/client";
import { useChartTheme } from "../theme";

interface Props {
  onLogin: (token: string, roles: string[]) => void;
}

export function LoginPage({ onLogin }: Props) {
  const theme = useChartTheme();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const canSubmit = username.trim().length > 0 && password.length > 0 && !submitting;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const token = await api.login(username.trim(), password);
      onLogin(token.access_token, token.roles);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign-in failed");
    } finally {
      setSubmitting(false);
    }
  };

  const inputStyle: React.CSSProperties = {
    display: "block",
    width: "100%",
    boxSizing: "border-box",
    padding: "8px 10px",
    marginBottom: 12,
    borderRadius: 6,
    border: `1px solid ${theme.gridline}`,
    background: theme.surface,
    color: theme.textPrimary,
    font: "inherit",
  };

  return (
    <main style={{ maxWidth: 360, margin: "10vh auto", padding: 24 }}>
      <h1 style={{ fontSize: 18, marginBottom: 4 }}>Time-Series Analytics</h1>
      <p style={{ fontSize: 13, color: theme.textSecondary, marginTop: 0 }}>
        Sign in to view devices, metrics and anomalies.
      </p>

      <form onSubmit={handleSubmit} noValidate>
        <label style={{ fontSize: 12, color: theme.textSecondary }}>
          Username
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
            style={inputStyle}
          />
        </label>
        <label style={{ fontSize: 12, color: theme.textSecondary }}>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
            style={inputStyle}
          />
        </label>

        {error && (
          <p role="alert" style={{ fontSize: 13, color: theme.statusCritical }}>
            ⚠ {error}
          </p>
        )}

        <button
          type="submit"
          disabled={!canSubmit}
          style={{
            padding: "8px 16px",
            borderRadius: 6,
            border: "none",
            background: canSubmit ? theme.series1 : theme.gridline,
            color: canSubmit ? "#ffffff" : theme.muted,
            font: "inherit",
            cursor: canSubmit ? "pointer" : "default",
          }}
        >
          {submitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}
