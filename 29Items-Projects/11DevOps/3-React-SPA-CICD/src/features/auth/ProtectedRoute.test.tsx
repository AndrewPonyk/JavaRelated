import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';

import { ProtectedRoute } from './ProtectedRoute';
import { renderWithProviders, seedAuthenticatedSession } from '@/test/testUtils';

function renderGuarded() {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<div>LOGIN_STUB</div>} />
      <Route element={<ProtectedRoute />}>
        <Route path="/secret" element={<div>SECRET_CONTENT</div>} />
      </Route>
    </Routes>,
    { route: '/secret' },
  );
}

describe('ProtectedRoute', () => {
  it('shows a session check while auth status is unknown, then redirects anonymous users', async () => {
    renderGuarded();

    expect(screen.getByRole('status')).toHaveTextContent(/checking your session/i);

    expect(await screen.findByText('LOGIN_STUB')).toBeInTheDocument();
    expect(screen.queryByText('SECRET_CONTENT')).not.toBeInTheDocument();
  });

  it('renders the protected outlet for authenticated users', async () => {
    seedAuthenticatedSession();
    renderGuarded();

    expect(await screen.findByText('SECRET_CONTENT')).toBeInTheDocument();
    expect(screen.queryByText('LOGIN_STUB')).not.toBeInTheDocument();
  });
});
