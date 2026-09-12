import { Suspense, useEffect } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';

import { env } from '@/app/env';
import { ConsentBanner } from '@/components/ConsentBanner';
import { LoadingSpinner } from '@/components/LoadingSpinner';
import { useAuth } from '@/features/auth/useAuth';
import { trackPageView } from '@/lib/analytics/analytics';

/** SPA page-view tracking: the router changes URLs without full loads, so GA must be told. */
function usePageViewTracking(): void {
  const location = useLocation();
  useEffect(() => {
    trackPageView(location.pathname);
  }, [location.pathname]);
}

export function AppLayout() {
  usePageViewTracking();
  const { user, status, logout } = useAuth();

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="app-header">
        <span className="app-header__brand">Customer Portal</span>
        {status === 'authenticated' && (
          <nav aria-label="Primary">
            <NavLink to="/" end>
              Dashboard
            </NavLink>
            <NavLink to="/settings">Settings</NavLink>
          </nav>
        )}
        {status === 'authenticated' && user && (
          <div className="app-header__user">
            <span aria-label="Signed in as">{user.email}</span>
            <button type="button" onClick={() => void logout()}>
              Sign out
            </button>
          </div>
        )}
      </header>

      <main id="main-content" className="app-main">
        {/* Suspense boundary for the lazy-loaded route chunks (src/app/router.tsx). */}
        <Suspense fallback={<LoadingSpinner label="Loading page…" />}>
          <Outlet />
        </Suspense>
      </main>

      <footer className="app-footer">
        <small>
          Customer Portal · {env.envName} · {env.appVersion.slice(0, 7)}
        </small>
      </footer>

      <ConsentBanner />
    </div>
  );
}
