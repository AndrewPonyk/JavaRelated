import { useState } from 'react';

import { useAuth } from './auth/AuthContext';
import { CategoriesManager } from './components/CategoriesManager';
import { ImageClassifier } from './components/ImageClassifier';
import { Login } from './components/Login';

type Tab = 'classify' | 'categories';

export default function App() {
  const { token, logout } = useAuth();
  const [tab, setTab] = useState<Tab>('classify');

  if (!token) return <Login />;

  return (
    <div className="app">
      <header className="topbar">
        <h1>Image Classification Console</h1>
        <button className="link" onClick={logout}>
          Sign out
        </button>
      </header>

      <nav className="tabs">
        <button className={tab === 'classify' ? 'active' : ''} onClick={() => setTab('classify')}>
          Classify
        </button>
        <button
          className={tab === 'categories' ? 'active' : ''}
          onClick={() => setTab('categories')}
        >
          Categories
        </button>
      </nav>

      <main className="content">
        {tab === 'classify' ? <ImageClassifier /> : <CategoriesManager />}
      </main>
    </div>
  );
}
