import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import CreateStrategyForm from '../components/CreateStrategyForm';

describe('CreateStrategyForm', () => {
  it('shows a validation error and does not submit when name is too short', async () => {
    const onCreate = vi.fn();
    render(<CreateStrategyForm onCreate={onCreate} />);
    // name starts empty (too short)
    fireEvent.click(screen.getByText('Create strategy'));
    await waitFor(() => expect(screen.getByText(/at least 3 characters/i)).toBeTruthy());
    expect(onCreate).not.toHaveBeenCalled();
  });

  it('submits a normalized payload when the form is valid', async () => {
    const onCreate = vi.fn().mockResolvedValue({});
    render(<CreateStrategyForm onCreate={onCreate} />);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'My Strategy' } });
    fireEvent.change(screen.getByLabelText('Symbols (comma-separated)'), {
      target: { value: 'aapl, msft' },
    });
    fireEvent.click(screen.getByText('Create strategy'));

    await waitFor(() => expect(onCreate).toHaveBeenCalledTimes(1));
    const payload = onCreate.mock.calls[0][0];
    expect(payload.name).toBe('My Strategy');
    expect(payload.symbols).toEqual(['AAPL', 'MSFT']);
    expect(payload.max_position_qty).toBe(1000);
  });
});
