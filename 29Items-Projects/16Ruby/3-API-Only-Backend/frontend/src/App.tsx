import { FormEvent, useEffect, useState } from 'react';
import { api, SyncItem, User } from './api/client';
import { UserSyncStatus } from './components/UserSyncStatus';
import './styles.css';

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [items, setItems] = useState<SyncItem[]>([]);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [noteTitle, setNoteTitle] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (!localStorage.getItem('jwt_token')) return;
    api.me().then(({ user: currentUser }) => {
      setUser(currentUser);
      return loadItems();
    }).catch(() => localStorage.removeItem('jwt_token'));
  }, []);

  async function loadItems() {
    const response = await api.syncItems();
    setItems(response.sync_items);
    setRefreshKey((value) => value + 1);
  }

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);

    try {
      if (!email.includes('@')) throw new Error('Enter a valid email address');
      if (password.length < 8) throw new Error('Password must be at least 8 characters');

      const response = mode === 'login' ? await api.login(email, password) : await api.register(email, password);
      localStorage.setItem('jwt_token', response.token);
      setUser(response.user);
      await loadItems();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed');
    } finally {
      setLoading(false);
    }
  }

  async function createNote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);

    try {
      if (noteTitle.trim().length < 2) throw new Error('Note title must be at least 2 characters');
      await api.createNote(noteTitle.trim());
      setNoteTitle('');
      await loadItems();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create note');
    } finally {
      setLoading(false);
    }
  }

  async function logout() {
    await api.logout();
    setUser(null);
    setItems([]);
  }

  if (!user) {
    return (
      <main className="app-shell auth-layout">
        <form className="panel auth-panel" onSubmit={submitAuth}>
          <h1>Backend Dashboard</h1>
          <div className="segmented">
            <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>Login</button>
            <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>Register</button>
          </div>
          <label>
            Email
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
          </label>
          <label>
            Password
            <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={8} required />
          </label>
          {error && <p className="error-text">{error}</p>}
          <button className="primary" disabled={loading}>{loading ? 'Working...' : mode === 'login' ? 'Login' : 'Create Account'}</button>
        </form>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <h1>Backend Dashboard</h1>
          <p>{user.email}</p>
        </div>
        <button onClick={logout}>Logout</button>
      </header>

      {error && <p className="error-text">{error}</p>}

      <div className="dashboard-grid">
        <UserSyncStatus refreshKey={refreshKey} />

        <section className="panel">
          <h3>Create Synced Note</h3>
          <form className="inline-form" onSubmit={createNote}>
            <input value={noteTitle} onChange={(event) => setNoteTitle(event.target.value)} placeholder="Note title" />
            <button className="primary" disabled={loading}>Add</button>
          </form>
        </section>
      </div>

      <section className="panel">
        <h3>Synced Records</h3>
        {items.length === 0 ? (
          <p>No records synced yet.</p>
        ) : (
          <ul className="record-list">
            {items.map((item) => (
              <li key={item.id}>
                <span>{item.collection_name}/{item.record_id}</span>
                <strong>{String(item.payload.title ?? 'Untitled')}</strong>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
