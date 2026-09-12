import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom';
import { ProductSearch } from '@/components/ProductSearch';
import { SellerDashboard } from '@/features/seller-dashboard/SellerDashboard';
import { ProductManager } from '@/features/seller-dashboard/ProductManager';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { AuthProvider } from '@/auth/AuthProvider';
import { useAuth } from '@/auth/AuthContext';

export default function App(): JSX.Element {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppShell />
      </AuthProvider>
    </BrowserRouter>
  );
}

function AppShell(): JSX.Element {
  const { isAuthenticated, isSeller, user, logout } = useAuth();

  return (
    <>
      <header>
        <h1>Marketplace</h1>
        <nav>
          <Link to="/">Browse</Link>
          {isSeller && (
            <>
              {' | '}
              <Link to="/sell">My products</Link>
              {' | '}
              <Link to="/seller">Dashboard</Link>
            </>
          )}
          {' | '}
          {isAuthenticated ? (
            <>
              <span>{user?.email}</span>{' '}
              <button type="button" onClick={logout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login">Log in</Link>
              {' | '}
              <Link to="/register">Register</Link>
            </>
          )}
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<ProductSearch />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/sell"
            element={
              <RequireSeller>
                <ProductManager />
              </RequireSeller>
            }
          />
          <Route
            path="/seller"
            element={
              <RequireSeller>
                <SellerDashboard />
              </RequireSeller>
            }
          />
          <Route path="*" element={<p>Not found.</p>} />
        </Routes>
      </main>
    </>
  );
}

/** Route guard: only sellers may reach catalogue management & the dashboard. */
function RequireSeller({ children }: { children: JSX.Element }): JSX.Element {
  const { isSeller, loading } = useAuth();
  if (loading) {
    return <p role="status">Loading…</p>;
  }
  return isSeller ? children : <Navigate to="/login" replace />;
}
