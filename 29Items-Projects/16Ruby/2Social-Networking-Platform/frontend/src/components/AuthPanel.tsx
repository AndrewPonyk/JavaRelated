import { FormEvent, useState } from 'react';
import { useMutation } from '@apollo/client';

import { LOGIN, REGISTER } from '../graphql/operations';
import { validateEmail, validatePassword, validateRequired } from '../lib/validation';
import type { User } from '../types/graphql';

type AuthPanelProps = {
  onAuthenticated: (session: { user: User; accessToken: string; refreshToken: string }) => void;
};

export function AuthPanel({ onAuthenticated }: AuthPanelProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [form, setForm] = useState({
    email: 'demo@example.com',
    username: 'demo_user',
    displayName: 'Demo User',
    password: 'password123',
  });
  const [formError, setFormError] = useState('');
  const [login, loginState] = useMutation(LOGIN);
  const [register, registerState] = useMutation(REGISTER);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError =
      validateEmail(form.email) ||
      validatePassword(form.password) ||
      (mode === 'register' ? validateRequired(form.username, 'Username') : '');

    if (validationError) {
      setFormError(validationError);
      return;
    }

    try {
      const result =
        mode === 'register'
          ? await register({
              variables: {
                ...form,
                email: form.email.trim(),
                username: form.username.trim(),
                displayName: form.displayName.trim(),
              },
            })
          : await login({ variables: { email: form.email.trim(), password: form.password } });

      const payload =
        mode === 'register'
          ? result.data?.registerUser?.authPayload
          : result.data?.loginUser?.authPayload;
      const errors =
        mode === 'register' ? result.data?.registerUser?.errors : result.data?.loginUser?.errors;

      if (!payload) {
        setFormError(errors?.join(', ') || 'Authentication failed');
        return;
      }

      setFormError('');
      onAuthenticated(payload);
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Authentication failed');
    }
  };

  const pending = loginState.loading || registerState.loading;

  return (
    <section className="auth-panel">
      <div className="segmented">
        <button className={mode === 'login' ? 'active' : ''} type="button" onClick={() => setMode('login')}>
          Sign in
        </button>
        <button className={mode === 'register' ? 'active' : ''} type="button" onClick={() => setMode('register')}>
          Register
        </button>
      </div>

      <form className="stack" onSubmit={submit}>
        <label>
          Email
          <input value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
        </label>

        {mode === 'register' ? (
          <>
            <label>
              Username
              <input value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} />
            </label>
            <label>
              Display name
              <input
                value={form.displayName}
                onChange={(event) => setForm({ ...form, displayName: event.target.value })}
              />
            </label>
          </>
        ) : null}

        <label>
          Password
          <input
            type="password"
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
          />
        </label>

        {formError ? <p className="inline-error">{formError}</p> : null}
        <button type="submit" disabled={pending}>
          {pending ? 'Working...' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>
      </form>
    </section>
  );
}
