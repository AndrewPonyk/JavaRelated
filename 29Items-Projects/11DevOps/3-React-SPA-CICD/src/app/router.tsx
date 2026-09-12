import { lazy } from 'react';
import { createBrowserRouter } from 'react-router-dom';

import { env } from '@/app/env';
import { RouteErrorFallback } from '@/components/ErrorBoundary';
import { AppLayout } from '@/components/layout/AppLayout';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';

// Route-level code splitting: each page is its own chunk (performance budgets —
// docs/ARCHITECTURE.md §2.4). AppLayout provides the Suspense boundary.
const LoginPage = lazy(() =>
  import('@/features/auth/LoginPage').then((m) => ({ default: m.LoginPage })),
);
const DashboardPage = lazy(() =>
  import('@/features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })),
);
const SettingsPage = lazy(() =>
  import('@/features/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })),
);

function NotFoundPage() {
  return (
    <section aria-labelledby="nf-heading">
      <h1 id="nf-heading">Page not found</h1>
      <p>The page you are looking for does not exist or has moved.</p>
    </section>
  );
}

export const router = createBrowserRouter(
  [
    {
      element: <AppLayout />,
      errorElement: <RouteErrorFallback />,
      children: [
        { path: '/login', element: <LoginPage /> },
        {
          element: <ProtectedRoute />,
          children: [
            { path: '/', element: <DashboardPage /> },
            { path: '/settings', element: <SettingsPage /> },
          ],
        },
        { path: '*', element: <NotFoundPage /> },
      ],
    },
  ],
  {
    // Follows the vite `--base` flag so PR previews served under /pr-<n>/ route correctly
    // (docs/TECH-NOTES.md §3.6 #4).
    basename: env.baseUrl,
  },
);
