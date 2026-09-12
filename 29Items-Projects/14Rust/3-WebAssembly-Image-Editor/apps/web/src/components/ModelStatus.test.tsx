import { render, screen } from '@testing-library/react';

import { ModelStatus } from './ModelStatus';

describe('ModelStatus', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders fetched model availability', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            version: '1.2.3',
            provider: 'heuristic',
            modelUrl: null,
            sha256: null,
            input: { width: 224, height: 224, layout: 'NCHW', normalization: 'zero-to-one' },
            output: { format: 'xywh-normalized', aspectRatio: 1 },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );

    render(<ModelStatus />);
    expect(screen.getByText(/checking auto-crop/i)).toBeInTheDocument();
    expect(await screen.findByText(/heuristic provider, version 1.2.3/i)).toBeInTheDocument();
  });

  it('keeps manual editing available when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    render(<ModelStatus />);
    expect(await screen.findByText(/manual editing still works/i)).toBeInTheDocument();
  });
});
