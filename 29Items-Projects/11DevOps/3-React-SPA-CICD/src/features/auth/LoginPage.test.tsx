import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { LoginPage } from './LoginPage';
import { DEMO_USER } from '../../../mocks/db/store';
import { renderWithProviders, seedAuthenticatedSession } from '@/test/testUtils';

function renderLogin(route = '/login') {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<div>DASHBOARD_STUB</div>} />
      <Route path="/settings" element={<div>SETTINGS_STUB</div>} />
    </Routes>,
    { route },
  );
}

describe('LoginPage', () => {
  it('shows zod field errors for invalid input without calling the API', async () => {
    renderLogin();
    await screen.findByRole('heading', { name: /sign in/i });

    await userEvent.type(screen.getByLabelText(/e-mail/i), 'not-an-email');
    await userEvent.type(screen.getByLabelText(/password/i), 'short');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText(/enter a valid e-mail address/i)).toBeInTheDocument();
    expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument();
    expect(screen.queryByText('DASHBOARD_STUB')).not.toBeInTheDocument();
  });

  it('shows a form-level error on wrong credentials and stays on the page', async () => {
    renderLogin();
    await screen.findByRole('heading', { name: /sign in/i });

    await userEvent.type(screen.getByLabelText(/e-mail/i), DEMO_USER.email);
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong-password-1');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/incorrect/i);
    expect(screen.queryByText('DASHBOARD_STUB')).not.toBeInTheDocument();
  });

  it('navigates to the return-to target after a successful login', async () => {
    renderLogin();
    await screen.findByRole('heading', { name: /sign in/i });

    await userEvent.type(screen.getByLabelText(/e-mail/i), DEMO_USER.email);
    await userEvent.type(screen.getByLabelText(/password/i), DEMO_USER.password);
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('DASHBOARD_STUB')).toBeInTheDocument();
  });

  it('redirects straight to the app when already authenticated', async () => {
    seedAuthenticatedSession();
    renderLogin();

    expect(await screen.findByText('DASHBOARD_STUB')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument();
  });
});
