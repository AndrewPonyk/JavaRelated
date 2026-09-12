import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiFetch, auth } from "../api/client.js";
import { ErrorText, Field, Panel } from "./ui.jsx";

/** Login / register. The demo token lives in sessionStorage (never localStorage). */
export default function Login() {
  const navigate = useNavigate();
  const [mode, setMode] = useState("login"); // 'login' | 'register'
  const [form, setForm] = useState({ email: "", password: "" });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState(null);
  const [busy, setBusy] = useState(false);

  // Post-register TOTP enrolment (optional) and login second-factor states.
  const [totpStep, setTotpStep] = useState(null); // null | 'enrol' | 'confirm' | 'login'
  const [totpSecret, setTotpSecret] = useState("");
  const [totpUri, setTotpUri] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [registeredAs, setRegisteredAs] = useState(null); // user dict after register

  function update(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  }

  function validate() {
    const found = {};
    if (!/^\S+@\S+\.\S+$/.test(form.email))
      found.email = ["Enter a valid email address"];
    if (mode === "register" && form.password.length < 8)
      found.password = ["Password must be at least 8 characters"];
    if (!form.password) found.password = ["Password is required"];
    setErrors(found);
    return Object.keys(found).length === 0;
  }

  async function submit(event) {
    event.preventDefault();
    setServerError(null);
    if (!validate()) return;
    setBusy(true);
    try {
      if (mode === "register") {
        const res = await apiFetch("/auth/register", {
          method: "POST",
          body: form,
        });
        auth.setToken(res.token);
        setRegisteredAs(res.user);
        setTotpStep("enrol"); // offer optional 2FA before moving on
      } else {
        try {
          const res = await apiFetch("/auth/login", {
            method: "POST",
            body: form,
          });
          auth.setToken(res.token);
          navigate(res.user.is_admin ? "/admin/audit" : "/");
        } catch (err) {
          if (/totp code required/i.test(err.message)) {
            setTotpStep("login"); // account has 2FA — ask for the code
          } else {
            throw err;
          }
        }
      }
    } catch (err) {
      setServerError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function startEnrolment() {
    setServerError(null);
    setBusy(true);
    try {
      const res = await apiFetch("/auth/totp/setup", {
        method: "POST",
        auth: true,
        body: {},
      });
      setTotpSecret(res.secret_base32);
      setTotpUri(res.otpauth_uri);
      setTotpStep("confirm");
    } catch (err) {
      setServerError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function submitTotp(event) {
    event.preventDefault();
    setServerError(null);
    if (!/^\d{6}$/.test(totpCode)) {
      setServerError("TOTP code must be exactly 6 digits");
      return;
    }
    setBusy(true);
    try {
      if (totpStep === "confirm") {
        await apiFetch("/auth/totp/enable", {
          method: "POST",
          auth: true,
          body: { code: totpCode, secret_base32: totpSecret },
        });
        setTotpStep(null);
        navigate("/");
      } else {
        const res = await apiFetch("/auth/login", {
          method: "POST",
          body: {
            email: form.email,
            password: form.password,
            totp_code: totpCode,
          },
        });
        auth.setToken(res.token);
        navigate(res.user.is_admin ? "/admin/audit" : "/");
      }
    } catch (err) {
      setServerError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (totpStep === "enrol") {
    return (
      <Panel
        title={`Welcome, ${registeredAs?.email ?? ""}`}
        subtitle={`Account created${registeredAs?.is_admin ? " — you are the admin (first registered user)`" : "."} Optionally enable TOTP two-factor now, or skip it.`}
      >
        <div className="actions">
          <button
            onClick={startEnrolment}
            disabled={busy}
            data-testid="totp-start"
          >
            Enable TOTP now
          </button>
          <button
            className="secondary"
            onClick={() => navigate("/")}
            disabled={busy}
          >
            Skip for now
          </button>
        </div>
        <ErrorText error={serverError} />
        <p className="hint">
          TOTP is RFC 6238: the secret never leaves the server unencrypted
          (Fernet at rest), and the code window is ±30 s. You can also manage it
          later via <code>/auth/totp/setup</code>.
        </p>
      </Panel>
    );
  }

  if (totpStep === "confirm" || totpStep === "login") {
    return (
      <Panel
        title={
          totpStep === "confirm"
            ? "Confirm your authenticator"
            : "Two-factor code required"
        }
        subtitle={
          totpStep === "confirm"
            ? "Scan the otpauth URI with any authenticator (Google Authenticator, Aegis, 1Password), then enter the current 6-digit code."
            : "This account has TOTP enabled. Enter the current 6-digit code from your authenticator."
        }
      >
        {totpStep === "confirm" && (
          <>
            <p className="hint">Secret (base32):</p>
            <pre className="mono-block" data-testid="totp-secret">
              {totpSecret}
            </pre>
            <p className="hint">otpauth URI:</p>
            <pre className="mono-block">{totpUri}</pre>
          </>
        )}
        <form onSubmit={submitTotp}>
          <Field label="6-digit code">
            <input
              inputMode="numeric"
              maxLength={6}
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ""))}
              data-testid="totp-code"
            />
          </Field>
          <ErrorText error={serverError} />
          <div className="actions">
            <button type="submit" disabled={busy} data-testid="totp-submit">
              {totpStep === "confirm" ? "Confirm & enable" : "Sign in"}
            </button>
          </div>
        </form>
      </Panel>
    );
  }

  return (
    <Panel
      title={mode === "login" ? "Sign in" : "Create account"}
      subtitle="Demo auth: Argon2id password hashing + optional TOTP. The first account registered becomes the admin."
    >
      <div className="tab-row">
        <button
          type="button"
          className={`tab ${mode === "login" ? "active" : ""}`}
          onClick={() => {
            setMode("login");
            setServerError(null);
          }}
        >
          Sign in
        </button>
        <button
          type="button"
          className={`tab ${mode === "register" ? "active" : ""}`}
          onClick={() => {
            setMode("register");
            setServerError(null);
          }}
        >
          Register
        </button>
      </div>
      {/* noValidate: jsdom/browsers block implicit submission on invalid type="email"
          fields — we surface our own field errors instead of native bubble UX */}
      <form onSubmit={submit} noValidate>
        <Field label="Email" error={errors.email}>
          <input
            type="email"
            value={form.email}
            onChange={(e) => update("email", e.target.value)}
            autoComplete="username"
            data-testid="login-email"
          />
        </Field>
        <Field label="Password" error={errors.password}>
          <input
            type="password"
            value={form.password}
            onChange={(e) => update("password", e.target.value)}
            autoComplete={
              mode === "login" ? "current-password" : "new-password"
            }
            data-testid="login-password"
          />
        </Field>
        <ErrorText error={serverError} />
        <div className="actions">
          <button type="submit" disabled={busy} data-testid="login-submit">
            {busy
              ? "Working…"
              : mode === "login"
                ? "Sign in"
                : "Create account"}
          </button>
        </div>
      </form>
      <p className="hint">
        Accounts gate lesson editing and the audit log — every crypto demo stays
        anonymous. Token is kept in <code>sessionStorage</code> and dies with
        the tab. Back to the <Link to="/">AES lab</Link>.
      </p>
    </Panel>
  );
}
