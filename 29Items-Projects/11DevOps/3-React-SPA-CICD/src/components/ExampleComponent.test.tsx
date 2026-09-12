import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';

import { ExampleComponent } from './ExampleComponent';
import { server } from '../../mocks/server';

// Integration-flavored unit test: exercises the REAL useFetch → httpClient → MSW chain
// (docs/TECH-NOTES.md §3.2). Seed data comes from mocks/db/store.ts.
describe('ExampleComponent', () => {
  it('shows a loading state, then renders announcements from the API', async () => {
    render(<ExampleComponent />);

    expect(screen.getByRole('status')).toHaveTextContent(/loading announcements/i);

    expect(await screen.findByRole('heading', { name: /announcements/i })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem').length).toBeGreaterThan(0);
  });

  it('renders the error state with a working Retry on server failure', async () => {
    server.use(
      http.get(
        '*/v1/announcements',
        () => HttpResponse.json({ code: 'INTERNAL', message: 'boom' }, { status: 500 }),
        { once: true }, // only the first request fails; the retry hits the healthy handler
      ),
    );

    render(<ExampleComponent />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/could not load announcements/i);

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByRole('heading', { name: /announcements/i })).toBeInTheDocument();
  });

  it('renders an empty state when the API returns no announcements', async () => {
    server.use(http.get('*/v1/announcements', () => HttpResponse.json([])));

    render(<ExampleComponent />);

    expect(await screen.findByText(/no announcements right now/i)).toBeInTheDocument();
  });
});
