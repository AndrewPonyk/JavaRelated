/**
 * Login / registration card with client-side validation mirroring the
 * backend's policy (valid email, password ≥ 8 chars). Server errors
 * (409 duplicate, 401 bad credentials) surface as the problem detail.
 */

import { useState, type FormEvent } from "react";
import { ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";

type Mode = "login" | "register";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function AuthPanel() {
  const { user, login, register, logout } = useAuth();
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) {
    return (
      <section className="card" aria-label="Account">
        <h2>Account</h2>
        <p>
          Signed in as <strong>{user.email}</strong> ({user.role})
        </p>
        <button type="button" onClick={() => void logout()}>
          Sign out
        </button>
      </section>
    );
  }

  const validate = (): string | null => {
    if (!EMAIL_PATTERN.test(email)) return "Enter a valid email address.";
    if (password.length < 8) return "Password must be at least 8 characters.";
    return null;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const problem = validate();
    setValidationError(problem);
    setServerError(null);
    if (problem) return;

    setBusy(true);
    try {
      if (mode === "login") {
        await login(email, password);
      } else {
        await register(email, password);
      }
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : "Could not reach the server.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card" aria-label="Sign in or register">
      <h2>{mode === "login" ? "Sign in" : "Create account"}</h2>

      <form onSubmit={(e) => void handleSubmit(e)} className="solver-form" noValidate>
        <label htmlFor="auth-email">Email</label>
        <input
          id="auth-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          required
        />

        <label htmlFor="auth-password">Password</label>
        <input
          id="auth-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete={mode === "login" ? "current-password" : "new-password"}
          required
        />

        {validationError && (
          <p role="alert" className="error-banner">
            {validationError}
          </p>
        )}
        {serverError && (
          <p role="alert" className="error-banner">
            {serverError}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? "Working…" : mode === "login" ? "Sign in" : "Register"}
        </button>
      </form>

      <button
        type="button"
        className="link-button"
        onClick={() => {
          setMode(mode === "login" ? "register" : "login");
          setValidationError(null);
          setServerError(null);
        }}
      >
        {mode === "login" ? "Need an account? Register" : "Have an account? Sign in"}
      </button>
    </section>
  );
}
