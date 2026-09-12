import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';

import { ActivityFeed } from './ActivityFeed';
import { db } from '../../../../mocks/db/store';
import { server } from '../../../../mocks/server';

describe('ActivityFeed', () => {
  it('renders the seeded entries with signed, formatted amounts', async () => {
    render(<ActivityFeed />);

    expect(await screen.findByRole('heading', { name: /recent activity/i })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(db.activity.length);

    const credit = new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: 'EUR',
      signDisplay: 'always',
    }).format(250);
    expect(screen.getByText(credit)).toBeInTheDocument();
  });

  it('requests a digest, not a ledger: the ?limit= pagination is honored end-to-end', async () => {
    // Seed more entries than the feed's limit of 10.
    for (let i = 0; i < 15; i++) {
      db.activity.push({
        id: `extra-${i}`,
        date: '2026-06-01T00:00:00.000Z',
        description: `Extra entry ${i}`,
        amount: -1,
        currency: 'EUR',
      });
    }

    render(<ActivityFeed />);

    await screen.findByRole('heading', { name: /recent activity/i });
    expect(screen.getAllByRole('listitem')).toHaveLength(10);
  });

  it('shows the empty state when there is no activity', async () => {
    db.activity = [];
    render(<ActivityFeed />);

    expect(await screen.findByText(/no account activity yet/i)).toBeInTheDocument();
  });

  it('shows a recoverable error state with a working retry', async () => {
    server.use(
      http.get(
        '*/v1/activity',
        () => HttpResponse.json({ code: 'INTERNAL', message: 'boom' }, { status: 500 }),
        { once: true },
      ),
    );

    render(<ActivityFeed />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/could not load your recent activity/i);

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByRole('heading', { name: /recent activity/i })).toBeInTheDocument();
  });

  it('the mock API rejects an out-of-range limit with a 400 problem (contract check)', async () => {
    const { api } = await import('@/api/httpClient');

    await expect(api.get('/v1/activity?limit=1000')).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
    });
  });
});
