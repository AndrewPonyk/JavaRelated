import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MetricsDashboard from './MetricsDashboard';
import * as client from '../api/client';

vi.mock('../api/client', async (importOriginal) => {
  const original = await importOriginal<typeof client>();
  return {
    ...original,
    getMetricDefinitions: vi.fn(),
    getAggregates: vi.fn(),
  };
});

const definitions = [
  {
    metricKey: 'orders.completed',
    displayName: 'Orders completed',
    unit: 'count',
    description: null,
    createdAt: '1970-01-01T00:00:00Z',
  },
];

const points = [
  { windowStart: '2026-07-01T10:00:00Z', count: 40, sum: 400, min: 5, max: 20, avg: 10 },
  { windowStart: '2026-07-01T10:01:00Z', count: 60, sum: 600, min: 5, max: 25, avg: 10 },
];

beforeEach(() => {
  vi.mocked(client.getMetricDefinitions).mockResolvedValue(definitions);
  vi.mocked(client.getAggregates).mockResolvedValue(points);
});

describe('MetricsDashboard', () => {
  it('renders tiles, sparkline and table after loading', async () => {
    render(<MetricsDashboard defsVersion={0} live={null} />);

    expect(await screen.findByText(/Orders completed — events per 1m window/)).toBeInTheDocument();
    expect(await screen.findByText('Events (range)')).toBeInTheDocument(); // tiles render after the series fetch
    expect(screen.getByText('100')).toBeInTheDocument(); // 40 + 60 total
    expect(screen.getByRole('img', { name: /events per window/i })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: /recent windows/i })).toBeInTheDocument();
    expect(screen.getByText('↑ 50.0% vs previous window')).toBeInTheDocument();
  });

  it('shows the empty state when there is no data', async () => {
    vi.mocked(client.getAggregates).mockResolvedValue([]);

    render(<MetricsDashboard defsVersion={0} live={null} />);

    expect(await screen.findByText(/no data in the selected range/i)).toBeInTheDocument();
  });

  it('shows an error state with a working retry', async () => {
    vi.mocked(client.getAggregates)
      .mockRejectedValueOnce(new Error('Failed to load series'))
      .mockResolvedValueOnce(points);

    render(<MetricsDashboard defsVersion={0} live={null} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to load series');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Events (range)')).toBeInTheDocument();
  });

  it('switches window size via the segmented control', async () => {
    render(<MetricsDashboard defsVersion={0} live={null} />);
    await screen.findByText('Events (range)');

    await userEvent.click(screen.getByRole('button', { name: '5m' }));

    await waitFor(() =>
      expect(client.getAggregates).toHaveBeenCalledWith('orders.completed', '5m', expect.anything()),
    );
  });

  it('folds matching live aggregates into the last-window tile', async () => {
    const { rerender } = render(<MetricsDashboard defsVersion={0} live={null} />);
    await screen.findByText('Events (range)');

    const live = {
      metricKey: 'orders.completed',
      windowSize: '1m',
      windowStart: new Date('2026-07-01T10:02:00Z').getTime(),
      windowEnd: 0,
      count: 7,
      sum: 70,
      min: 1,
      max: 20,
      avg: 10,
      dimensions: {},
    };
    rerender(<MetricsDashboard defsVersion={0} live={live} />);
    rerender(<MetricsDashboard defsVersion={0} live={{ ...live, count: 5, sum: 50 }} />);

    // 7 + 5 accumulated for the newest window
    expect(await screen.findByText('12')).toBeInTheDocument();
  });
});
