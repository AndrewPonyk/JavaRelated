import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { api, isAuthenticated } from '@/api/client';
import { LoginPage } from '@/pages/LoginPage';
import { ViewerPage } from '@/pages/ViewerPage';

function RequireAuth({ children }: { children: ReactNode }) {
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />;
}

function Header() {
  const navigate = useNavigate();
  return (
    <header className="app__header">
      <h1>🩻 Medical Imaging Platform</h1>
      <button
        className="app__logout"
        onClick={() => {
          api.logout();
          navigate('/login');
        }}
      >
        Sign out
      </button>
    </header>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <div className="app">
              <Header />
              <main>
                <ViewerPage />
              </main>
            </div>
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
