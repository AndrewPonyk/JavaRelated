import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { AuthProvider } from './AuthProvider';
import { useAuth } from './useAuth';
import { DEMO_USER } from '../../../mocks/db/store';

/** Minimal consumer that exposes the auth state machine for assertions. */
function Probe() {
  const { status, user, login, logout } = useAuth();
  return (
    <div>
      <output data-testid="status">{status}</output>
      <output data-testid="email">{user?.email ?? ''}</output>
      <button onClick={() => void login({ email: DEMO_USER.email, password: DEMO_USER.password })}>
        login
      </button>
      <button onClick={() => void logout()}>logout</button>
    </div>
  );
}

describe('AuthProvider', () => {
  it('resolves to anonymous when no session exists (initial /me returns 401)', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    expect(screen.getByTestId('status')).toHaveTextContent('unknown');
    // findByText waits for the TEXT to appear (findByTestId would resolve immediately
    // on the element while it still reads 'unknown').
    expect(await screen.findByText('anonymous')).toBeInTheDocument();
  });

  it('login() transitions to authenticated with the user loaded, logout() reverses it', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
    await screen.findByText('anonymous');

    await userEvent.click(screen.getByRole('button', { name: 'login' }));
    await screen.findByText('authenticated');
    expect(screen.getByTestId('email')).toHaveTextContent(DEMO_USER.email);

    await userEvent.click(screen.getByRole('button', { name: 'logout' }));
    await screen.findByText('anonymous');
    expect(screen.getByTestId('email')).toHaveTextContent('');
  });

  // The wrong-credentials journey (error message, staying anonymous) is covered where it
  // belongs: through the real form in e2e/auth.spec.ts.
});
