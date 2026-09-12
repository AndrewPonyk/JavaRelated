import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ProductManager } from './ProductManager';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ProductManager', () => {
  it('renders the create form and the empty state', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      json: () => Promise.resolve({ items: [] }),
    });

    render(<ProductManager />);

    expect(screen.getByRole('form', { name: /create product/i })).toBeInTheDocument();
    expect(await screen.findByText(/no products yet/i)).toBeInTheDocument();
  });

  it('lists the seller’s existing products', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      json: () =>
        Promise.resolve({
          items: [
            {
              id: 'p1',
              sellerId: 's1',
              name: 'Keyboard',
              description: 'Mechanical',
              price: { amountMinor: 7999, currency: 'USD' },
              stock: 3,
              active: true,
            },
          ],
        }),
    });

    render(<ProductManager />);

    expect(await screen.findByText('Keyboard')).toBeInTheDocument();
  });
});
