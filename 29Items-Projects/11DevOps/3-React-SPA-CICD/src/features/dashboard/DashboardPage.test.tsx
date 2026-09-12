import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';

import { DashboardPage } from './DashboardPage';
import { server } from '../../../mocks/server';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { seedAuthenticatedSession } from '@/test/testUtils';

// Deterministic experiment variants for UI assertions; assignment logic itself is
// covered by abTesting.test.ts.
jest.mock('@/lib/analytics/abTesting', () => ({
  ...jest.requireActual('@/lib/analytics/abTesting'),
  useExperiment: jest.fn((experimentId: string) =>
    experimentId === 'onboarding-checklist' ? 'treatment' : 'control',
  ),
}));

function renderDashboard() {
  seedAuthenticatedSession();
  return render(
    <AuthProvider>
      <DashboardPage />
    </AuthProvider>,
  );
}

describe('DashboardPage', () => {
  it('renders the summary cards with formatted values from the API', async () => {
    renderDashboard();

    expect(
      await screen.findByRole('heading', { name: /welcome back, demo customer/i }),
    ).toBeInTheDocument();

    const summary = await screen.findByRole('region', { name: /account summary/i });
    expect(summary).toBeInTheDocument();

    const expectedBalance = new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: 'EUR',
    }).format(2417.53);
    expect(screen.getByText(expectedBalance)).toBeInTheDocument();

    expect(screen.getByRole('heading', { name: /open tickets/i })).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText(/awaiting response/i)).toBeInTheDocument();
  });

  it('renders the balance trend sparkline with an accessible description', async () => {
    renderDashboard();

    const sparkline = await screen.findByRole('img', { name: /balance up .* since 2026-02/i });
    expect(sparkline).toBeInTheDocument();
  });

  it('renders the recent activity feed with signed amounts', async () => {
    renderDashboard();

    expect(await screen.findByRole('heading', { name: /recent activity/i })).toBeInTheDocument();
    expect(screen.getByText(/monthly subscription payment/i)).toBeInTheDocument();
    expect(screen.getByText(/refund — support ticket/i)).toBeInTheDocument();
    expect(screen.getByText(/account top-up/i)).toBeInTheDocument();
  });

  it('shows the onboarding checklist for the treatment variant and lets the user dismiss it', async () => {
    renderDashboard();

    const checklist = await screen.findByRole('heading', { name: /get started/i });
    expect(checklist).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /dismiss getting-started/i }));
    expect(screen.queryByRole('heading', { name: /get started/i })).not.toBeInTheDocument();
    expect(window.localStorage.getItem('portal.onboarding-dismissed')).toBe('true');
  });

  it('shows a recoverable error state when the summary fails to load', async () => {
    server.use(
      http.get(
        '*/v1/dashboard/summary',
        () => HttpResponse.json({ code: 'INTERNAL', message: 'boom' }, { status: 500 }),
        { once: true },
      ),
    );

    renderDashboard();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/could not load your account summary/i);

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByRole('region', { name: /account summary/i })).toBeInTheDocument();
  });

  it('renders the announcements section (reference fetch pattern)', async () => {
    renderDashboard();

    expect(await screen.findByRole('heading', { name: /announcements/i })).toBeInTheDocument();
  });
});
