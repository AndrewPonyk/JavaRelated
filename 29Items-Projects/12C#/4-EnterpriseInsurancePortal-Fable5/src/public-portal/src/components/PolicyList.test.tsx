import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PolicySummary } from '../api/client';
import { PolicyList } from './PolicyList';

const policies: PolicySummary[] = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    policyNumber: 'POL-20260612-ABCD1234',
    customerId: '22222222-2222-2222-2222-222222222222',
    annualPremium: 1625.5,
    effectiveDate: '2026-06-12',
    expiryDate: '2027-06-12',
    status: 'Active',
  },
];

function mockFetchOnce(body: unknown, ok = true, status = 200) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok,
      status,
      json: () => Promise.resolve(body),
    }),
  );
}

describe('PolicyList', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('shows a loading state, then the policies', async () => {
    mockFetchOnce(policies);
    render(<PolicyList selectedPolicyId={null} onSelect={() => {}} />);

    expect(screen.getByText(/loading your policies/i)).toBeTruthy();
    await waitFor(() => expect(screen.getByText('POL-20260612-ABCD1234')).toBeTruthy());
    expect(screen.getByText('Active')).toBeTruthy();
  });

  it('shows an empty state when the customer has no policies', async () => {
    mockFetchOnce([]);
    render(<PolicyList selectedPolicyId={null} onSelect={() => {}} />);

    await waitFor(() => expect(screen.getByText(/no policies yet/i)).toBeTruthy());
  });

  it('shows the server problem detail on error', async () => {
    mockFetchOnce({ title: 'Server error', detail: 'Database offline.' }, false, 500);
    render(<PolicyList selectedPolicyId={null} onSelect={() => {}} />);

    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Database offline.'));
  });

  it('invokes onSelect when a policy row is clicked', async () => {
    mockFetchOnce(policies);
    const onSelect = vi.fn();
    render(<PolicyList selectedPolicyId={null} onSelect={onSelect} />);

    await waitFor(() => expect(screen.getByText('POL-20260612-ABCD1234')).toBeTruthy());
    screen.getByText('POL-20260612-ABCD1234').closest('tr')!.click();
    expect(onSelect).toHaveBeenCalledWith(policies[0]);
  });
});
