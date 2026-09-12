import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ProductSearch } from './ProductSearch';

function mockFetchJson(body: unknown, ok = true, status = 200): void {
  globalThis.fetch = vi.fn().mockResolvedValue({
    ok,
    status,
    statusText: 'OK',
    json: () => Promise.resolve(body),
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ProductSearch', () => {
  it('shows the idle prompt before the user types', () => {
    render(<ProductSearch />);
    expect(screen.getByText(/start typing to search/i)).toBeInTheDocument();
  });

  it('renders results returned by the search API', async () => {
    mockFetchJson({
      total: 1,
      items: [{ id: '1', sellerId: 's1', name: 'Wireless Mouse', priceMinor: 2599, currency: 'USD' }],
    });

    render(<ProductSearch />);
    fireEvent.change(screen.getByLabelText(/search products/i), { target: { value: 'mouse' } });

    expect(await screen.findByText('Wireless Mouse')).toBeInTheDocument();
    expect(screen.getByText(/1 result/i)).toBeInTheDocument();
  });

  it('shows an empty state when nothing matches', async () => {
    mockFetchJson({ total: 0, items: [] });

    render(<ProductSearch />);
    fireEvent.change(screen.getByLabelText(/search products/i), { target: { value: 'zzz' } });

    expect(await screen.findByText(/no products match/i)).toBeInTheDocument();
  });

  it('surfaces an error when the API fails', async () => {
    mockFetchJson({ error: { message: 'boom' } }, false, 500);

    render(<ProductSearch />);
    fireEvent.change(screen.getByLabelText(/search products/i), { target: { value: 'mouse' } });

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
