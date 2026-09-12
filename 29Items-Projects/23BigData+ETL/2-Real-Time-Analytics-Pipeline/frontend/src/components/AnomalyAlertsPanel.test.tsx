import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AnomalyAlertsPanel from './AnomalyAlertsPanel';
import * as client from '../api/client';
import type { AnomalyAlert } from '../types/metrics';

vi.mock('../api/client', async (importOriginal) => {
  const original = await importOriginal<typeof client>();
  return {
    ...original,
    getAlerts: vi.fn(),
    acknowledgeAlert: vi.fn(),
  };
});

const alert: AnomalyAlert = {
  alertId: 'a-1',
  metricKey: 'orders.completed',
  severity: 'critical',
  score: 6.3,
  observed: 182,
  expected: 46.5,
  windowStart: '2026-07-01T10:00:00Z',
  detectedAt: '2026-07-01T10:00:01Z',
  status: 'open',
  ackedBy: null,
};

beforeEach(() => {
  vi.mocked(client.getAlerts).mockResolvedValue([alert]);
  vi.mocked(client.acknowledgeAlert).mockResolvedValue({ ...alert, status: 'acknowledged' });
});

describe('AnomalyAlertsPanel', () => {
  it('lists open alerts with icon + label severity', async () => {
    render(<AnomalyAlertsPanel alertsVersion={0} />);

    expect(await screen.findByText('orders.completed')).toBeInTheDocument();
    expect(screen.getByText(/▲ critical/)).toBeInTheDocument();
    expect(screen.getByText(/observed 182/)).toBeInTheDocument();
  });

  it('acknowledges optimistically', async () => {
    render(<AnomalyAlertsPanel alertsVersion={0} />);
    await screen.findByText('orders.completed');

    await userEvent.click(screen.getByRole('button', { name: 'Ack' }));

    await waitFor(() => expect(client.acknowledgeAlert).toHaveBeenCalledWith('a-1'));
    expect(screen.queryByText('orders.completed')).not.toBeInTheDocument();
    expect(await screen.findByText(/no open alerts/i)).toBeInTheDocument();
  });

  it('reverts the optimistic removal when acknowledge fails', async () => {
    vi.mocked(client.acknowledgeAlert).mockRejectedValue(new Error('Acknowledge failed'));

    render(<AnomalyAlertsPanel alertsVersion={0} />);
    await screen.findByText('orders.completed');

    await userEvent.click(screen.getByRole('button', { name: 'Ack' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Acknowledge failed');
  });

  it('refetches when the SSE alert version bumps', async () => {
    const { rerender } = render(<AnomalyAlertsPanel alertsVersion={0} />);
    await screen.findByText('orders.completed');
    expect(vi.mocked(client.getAlerts)).toHaveBeenCalledTimes(1);

    rerender(<AnomalyAlertsPanel alertsVersion={1} />);

    await waitFor(() => expect(vi.mocked(client.getAlerts)).toHaveBeenCalledTimes(2));
  });
});
