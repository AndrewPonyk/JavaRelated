import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';

import { credentialsSchema } from './auth.api';
import { useAuth } from './useAuth';
import { ApiError } from '@/api/httpClient';

type FieldErrors = Partial<Record<'email' | 'password', string>>;

export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const returnTo =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/';

  // Already signed in (e.g. back button) → straight to the app.
  if (status === 'authenticated') {
    return <Navigate to={returnTo} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setFormError(null);

    // Client-side zod validation — UX only; the API re-validates (§2.5).
    const parsed = credentialsSchema.safeParse({ email, password });
    if (!parsed.success) {
      const errors: FieldErrors = {};
      for (const issue of parsed.error.issues) {
        const field = issue.path[0];
        if (field === 'email' || field === 'password') errors[field] = issue.message;
      }
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});

    setSubmitting(true);
    try {
      await login(parsed.data);
      navigate(returnTo, { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFormError('E-mail or password is incorrect.');
      } else if (error instanceof ApiError && error.code === 'NETWORK_ERROR') {
        setFormError('You appear to be offline. Check your connection and try again.');
      } else {
        setFormError('Sign-in is temporarily unavailable. Please try again in a moment.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="login" aria-labelledby="login-heading">
      <h1 id="login-heading">Sign in</h1>

      {formError && (
        <div role="alert" className="error-panel">
          {formError}
        </div>
      )}

      <form onSubmit={(e) => void handleSubmit(e)} noValidate>
        <div className="form-field">
          <label htmlFor="email">E-mail</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={Boolean(fieldErrors.email)}
            aria-describedby={fieldErrors.email ? 'email-error' : undefined}
          />
          {fieldErrors.email && (
            <p id="email-error" className="field-error">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby={fieldErrors.password ? 'password-error' : undefined}
          />
          {fieldErrors.password && (
            <p id="password-error" className="field-error">
              {fieldErrors.password}
            </p>
          )}
        </div>

        <button type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </section>
  );
}
