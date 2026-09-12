import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CreateMetricForm from './CreateMetricForm';
import * as client from '../api/client';
import { ApiError } from '../api/client';

vi.mock('../api/client', async (importOriginal) => {
  const original = await importOriginal<typeof client>();
  return {
    ...original,
    createMetricDefinition: vi.fn(),
  };
});

beforeEach(() => {
  vi.mocked(client.createMetricDefinition).mockResolvedValue({
    metricKey: 'carts.abandoned',
    displayName: 'Carts abandoned',
    unit: null,
    description: null,
    createdAt: '2026-07-03T00:00:00Z',
  });
});

describe('CreateMetricForm', () => {
  it('blocks submission on client-side validation errors', async () => {
    render(<CreateMetricForm onCreated={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/metric key/i), 'NOT VALID KEY');
    await userEvent.click(screen.getByRole('button', { name: /register metric/i }));

    expect(await screen.findByText(/dot-separated lowercase/i)).toBeInTheDocument();
    expect(screen.getByText('Required')).toBeInTheDocument(); // display name
    expect(client.createMetricDefinition).not.toHaveBeenCalled();
  });

  it('submits a valid form and notifies the parent', async () => {
    const onCreated = vi.fn();
    render(<CreateMetricForm onCreated={onCreated} />);

    await userEvent.type(screen.getByLabelText(/metric key/i), 'carts.abandoned');
    await userEvent.type(screen.getByLabelText(/display name/i), 'Carts abandoned');
    await userEvent.click(screen.getByRole('button', { name: /register metric/i }));

    expect(await screen.findByText(/registered/i)).toBeInTheDocument();
    expect(client.createMetricDefinition).toHaveBeenCalledWith({
      metricKey: 'carts.abandoned',
      displayName: 'Carts abandoned',
      unit: null,
      description: null,
    });
    expect(onCreated).toHaveBeenCalled();
    expect(screen.getByLabelText(/metric key/i)).toHaveValue(''); // form reset
  });

  it('surfaces server conflicts and field errors', async () => {
    vi.mocked(client.createMetricDefinition).mockRejectedValue(
      new ApiError(409, 'Conflict', "Metric 'carts.abandoned' already exists"),
    );
    render(<CreateMetricForm onCreated={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/metric key/i), 'carts.abandoned');
    await userEvent.type(screen.getByLabelText(/display name/i), 'Carts abandoned');
    await userEvent.click(screen.getByRole('button', { name: /register metric/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/already exists/);
  });
});
