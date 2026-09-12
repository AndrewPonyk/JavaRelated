import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AnalysisDashboard } from './AnalysisDashboard';

const analysis = {
  id: '11111111-1111-1111-1111-111111111111',
  project_name: 'demo',
  source_path: 'sample.cpp',
  rule_pack: 'default',
  status: 'completed',
  findings: [
    {
      id: '22222222-2222-2222-2222-222222222222',
      rule_id: 'security.unsafe-call',
      severity: 'high',
      message: 'Unsafe call',
      file_path: 'sample.cpp',
      line: 4,
      column: 3,
      evidence: { call: 'system' },
    },
  ],
  ast_facts: {
    schemaVersion: '1.0.0',
    sourcePath: 'sample.cpp',
    functions: [{ name: 'main', line: 1, column: 1 }],
    calls: [{ name: 'system', line: 4, column: 3 }],
  },
  error_message: null,
  created_at: '2026-05-02T00:00:00Z',
  updated_at: '2026-05-02T00:00:00Z',
};

describe('AnalysisDashboard', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = input.toString();
        if (url === '/api/rules') {
          return json([{ id: 'security.unsafe-call', rule_pack: 'default', severity: 'high', description: 'unsafe', enabled: true }]);
        }
        if (url.startsWith('/api/analyses/') && init?.method === 'DELETE') {
          return new Response(null, { status: 204 });
        }
        if (url === '/api/analyses' && init?.method === 'POST') {
          return json(analysis, 201);
        }
        if (url === '/api/analyses') {
          return json([
            {
              id: analysis.id,
              project_name: analysis.project_name,
              source_path: analysis.source_path,
              rule_pack: 'default',
              status: 'completed',
              finding_count: 1,
              highest_severity: 'high',
              created_at: analysis.created_at,
              updated_at: analysis.updated_at,
            },
          ]);
        }
        if (url === `/api/analyses/${analysis.id}`) {
          return json(analysis);
        }
        return json({ detail: 'not found' }, 404);
      },
    );
  });

  afterEach(() => {
    cleanup();
    fetchSpy.mockRestore();
  });

  it('loads analyses and renders findings', async () => {
    render(<AnalysisDashboard />);

    expect(await screen.findByRole('heading', { name: 'demo' })).toBeInTheDocument();
    expect(await screen.findByText('security.unsafe-call')).toBeInTheDocument();
    expect(screen.getByText('Unsafe call')).toBeInTheDocument();
  });

  it('submits a new analysis', async () => {
    const user = userEvent.setup();
    render(<AnalysisDashboard />);

    await screen.findByRole('heading', { name: 'demo' });
    await user.click(screen.getByRole('button', { name: 'Run' }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        '/api/analyses',
        expect.objectContaining({ method: 'POST' }),
      );
    });
  });
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
