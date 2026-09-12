'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/context/AuthContext';

export default function LoginPage() {
  const { login, register } = useAuth();
  const router = useRouter();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('demo');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('password123');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (username.trim().length < 3) return setError('Username must be at least 3 characters.');
    if (password.length < 8) return setError('Password must be at least 8 characters.');
    if (mode === 'register' && !email.includes('@')) return setError('Enter a valid email.');
    setBusy(true);
    try {
      if (mode === 'login') await login(username.trim(), password);
      else await register(username.trim(), email.trim(), password);
      router.push('/');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Authentication failed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <h2>{mode === 'login' ? 'Log in' : 'Create an account'}</h2>
      <p className="muted">
        Demo account: <code>demo</code> / <code>password123</code>
      </p>
      <form onSubmit={submit} className="stacked">
        {error && <p className="error">{error}</p>}
        <input placeholder="Username" value={username} onChange={(e) => setUsername(e.target.value)} />
        {mode === 'register' && (
          <input placeholder="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        )}
        <input
          placeholder="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <button type="submit" className="btn" disabled={busy}>
          {busy ? '…' : mode === 'login' ? 'Log in' : 'Register'}
        </button>
      </form>
      <p style={{ marginTop: '1rem' }}>
        <button
          type="button"
          className="linklike"
          onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
        >
          {mode === 'login' ? 'Need an account? Register' : 'Have an account? Log in'}
        </button>
      </p>
    </section>
  );
}
