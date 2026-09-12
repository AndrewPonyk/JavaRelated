import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!username || !password) {
      setError("Username and password are required.");
      return;
    }
    setSubmitting(true);
    try {
      await login(username, password);
      navigate("/studies", { replace: true });
    } catch {
      setError("Invalid credentials. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="card login" onSubmit={onSubmit}>
        <h1>Clinical Trial Management</h1>
        <p className="muted">Sign in to continue</p>
        {error && <div role="alert" className="error-box">{error}</div>}
        <label>
          Username
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? "Signing in…" : "Sign in"}
        </button>
        <p className="hint muted">Demo: <code>crc</code> / <code>crc-password-123</code></p>
      </form>
    </div>
  );
}
