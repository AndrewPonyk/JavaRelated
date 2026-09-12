import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ConsentBanner } from './ConsentBanner';
import { resetAnalyticsForTests } from '@/lib/analytics/analytics';
import { setTestEnv } from '@/test/env.mock';

// The banner only exists when analytics is configured — give the env a measurement id.
beforeEach(() => {
  setTestEnv({ gaMeasurementId: 'G-TEST123' });
});

afterEach(() => {
  resetAnalyticsForTests();
  delete window.dataLayer;
  delete window.gtag;
});

describe('ConsentBanner', () => {
  it('shows the banner when no decision has been stored', () => {
    render(<ConsentBanner />);
    expect(screen.getByRole('region', { name: /analytics consent/i })).toBeInTheDocument();
  });

  it('persists and hides on "Allow analytics"', async () => {
    render(<ConsentBanner />);

    await userEvent.click(screen.getByRole('button', { name: /allow analytics/i }));

    expect(window.localStorage.getItem('portal.analytics-consent')).toBe('granted');
    expect(screen.queryByRole('region', { name: /analytics consent/i })).not.toBeInTheDocument();
  });

  it('persists and hides on "Decline"', async () => {
    render(<ConsentBanner />);

    await userEvent.click(screen.getByRole('button', { name: /decline/i }));

    expect(window.localStorage.getItem('portal.analytics-consent')).toBe('denied');
    expect(screen.queryByRole('region', { name: /analytics consent/i })).not.toBeInTheDocument();
  });

  it('never renders once a decision exists', () => {
    window.localStorage.setItem('portal.analytics-consent', 'denied');
    render(<ConsentBanner />);
    expect(screen.queryByRole('region', { name: /analytics consent/i })).not.toBeInTheDocument();
  });
});
