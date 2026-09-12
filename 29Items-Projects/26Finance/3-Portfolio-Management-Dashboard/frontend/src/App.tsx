import type { ReactNode } from "react";
import { Link, Navigate, Route, Routes } from "react-router-dom";

import { useAuth } from "./auth/AuthContext";
import { AssetsPage } from "./pages/AssetsPage";
import { LoginPage } from "./pages/LoginPage";
import { PortfolioDetailPage } from "./pages/PortfolioDetailPage";
import { PortfoliosPage } from "./pages/PortfoliosPage";
import { RegisterPage } from "./pages/RegisterPage";

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <p className="muted">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  const { user, logout } = useAuth();

  return (
    <div>
      <header className="nav">
        <Link to="/" className="brand">
          📈 Portfolio Dashboard
        </Link>
        <nav className="nav-links">
          {user && (
            <>
              <Link to="/">Portfolios</Link>
              <Link to="/assets">Assets</Link>
              <span className="muted">{user.email}</span>
              <button className="link" onClick={logout}>
                Logout
              </button>
            </>
          )}
        </nav>
      </header>

      <main className="container">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <PortfoliosPage />
              </RequireAuth>
            }
          />
          <Route
            path="/assets"
            element={
              <RequireAuth>
                <AssetsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/portfolios/:id"
            element={
              <RequireAuth>
                <PortfolioDetailPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
