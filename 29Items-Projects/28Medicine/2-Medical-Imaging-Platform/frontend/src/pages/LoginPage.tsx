// Login screen. Client-side validation + surfaced auth errors.

import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/api/client';

export function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string; form?: string }>({});
  const [submitting, setSubmitting] = useState(false);

  function validate(): boolean {
    const next: typeof errors = {};
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) next.email = 'Enter a valid email';
    if (password.length < 8) next.password = 'Password must be at least 8 characters';
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!validate()) return;
    setSubmitting(true);
    setErrors({});
    try {
      await api.login(email, password);
      navigate('/');
    } catch {
      setErrors({ form: 'Invalid email or password' });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login">
      <form className="login__card" onSubmit={onSubmit} noValidate>
        <h1>Medical Imaging Platform</h1>
        <p className="login__subtitle">Sign in to access the radiology worklist</p>

        {errors.form && (
          <div className="state state--error" role="alert">
            {errors.form}
          </div>
        )}

        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          value={email}
          autoComplete="username"
          onChange={(e) => setEmail(e.target.value)}
          aria-invalid={Boolean(errors.email)}
        />
        {errors.email && <span className="field-error">{errors.email}</span>}

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          autoComplete="current-password"
          onChange={(e) => setPassword(e.target.value)}
          aria-invalid={Boolean(errors.password)}
        />
        {errors.password && <span className="field-error">{errors.password}</span>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="login__hint">
          Dev seed: <code>radiologist@medimaging.local</code> / <code>radiology123</code>
        </p>
      </form>
    </div>
  );
}
