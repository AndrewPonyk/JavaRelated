import type { PropsWithChildren } from 'react';

import { ErrorBoundary } from '@/components/ErrorBoundary';
import { AuthProvider } from '@/features/auth/AuthProvider';

/**
 * Cross-cutting provider composition, kept in one place so App.tsx stays declarative.
 * Future providers (QueryClientProvider when server-state caching is adopted, theme,
 * i18n — see docs/ARCHITECTURE.md §2.2) slot in here without touching App.tsx.
 */
export function AppProviders({ children }: PropsWithChildren) {
  return (
    <ErrorBoundary>
      <AuthProvider>{children}</AuthProvider>
    </ErrorBoundary>
  );
}
