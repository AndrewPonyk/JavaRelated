import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { App } from './App';
import { DEMO_USER } from '../mocks/db/store';

/**
 * End-to-end-in-jsdom integration: the REAL router (lazy chunks, basename), providers,
 * auth flow, and mock API together. Complements the browser-level Playwright suite.
 */
describe('App integration', () => {
  it('redirects anonymous users to login, signs in, and lands on the dashboard', async () => {
    render(<App />);

    // Anonymous boot → login screen (via ProtectedRoute redirect + lazy chunk load).
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/e-mail/i), DEMO_USER.email);
    await userEvent.type(screen.getByLabelText(/password/i), DEMO_USER.password);
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    // Dashboard renders with live data from the mock API.
    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
    expect(await screen.findByRole('region', { name: /account summary/i })).toBeInTheDocument();

    // Shell chrome is present: primary nav + signed-in user + footer version.
    expect(screen.getByRole('navigation', { name: /primary/i })).toBeInTheDocument();
    expect(screen.getByText(DEMO_USER.email)).toBeInTheDocument();

    // Sign out returns to the login screen.
    await userEvent.click(screen.getByRole('button', { name: /sign out/i }));
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
  });
});
