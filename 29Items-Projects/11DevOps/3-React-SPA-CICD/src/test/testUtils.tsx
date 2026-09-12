import { render, type RenderOptions } from '@testing-library/react';
import type { PropsWithChildren, ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';

import { db } from '../../mocks/db/store';
import { tokenStore } from '@/api/httpClient';
import { AuthProvider } from '@/features/auth/AuthProvider';

/**
 * Puts the mock backend + token store into a "already signed in" state, so AuthProvider's
 * boot-time /v1/auth/me succeeds immediately. Cleaned up globally in setupTests afterEach.
 */
export function seedAuthenticatedSession(): void {
  const token = 'mock-token-user-1';
  db.validTokens.add(token);
  db.sessionActive = true;
  tokenStore.set(token);
}

interface ProviderRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  /** Initial URL for the in-memory router. */
  route?: string;
}

/**
 * render() with the app's provider stack — use for components that need auth or routing.
 * Components without those needs should use plain @testing-library/react render.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', ...options }: ProviderRenderOptions = {},
) {
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <AuthProvider>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </AuthProvider>
    );
  }
  return render(ui, { wrapper: Wrapper, ...options });
}

// Re-export for one-stop imports in tests.
export * from '@testing-library/react';
