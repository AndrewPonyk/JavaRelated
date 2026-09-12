import { screen, waitFor } from '@testing-library/dom';
import { defineStatusCard } from '../StatusCard';

describe('StatusCardElement', () => {
  beforeAll(() => {
    defineStatusCard();
  });

  afterEach(() => {
    document.body.innerHTML = '';
    jest.restoreAllMocks();
  });

  it('renders static status content', async () => {
    document.body.innerHTML = `
      <ds-status-card
        heading="Token sync completed"
        description="Figma tokens are aligned."
        status="success"
      ></ds-status-card>
    `;

    expect(await screen.findByText('Token sync completed')).not.toBeNull();
    expect(screen.getByText('Figma tokens are aligned.')).not.toBeNull();
  });

  it('renders remote data from data-src', async () => {
    Object.defineProperty(globalThis, 'fetch', {
      writable: true,
      value: jest.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          title: 'Remote status',
          description: 'Loaded from API',
          status: 'warning'
        })
      } satisfies Partial<Response>)
    });

    document.body.innerHTML = '<ds-status-card data-src="/api/status"></ds-status-card>';

    await waitFor(() => {
      expect(screen.getByText('Remote status')).not.toBeNull();
    });
  });
});
