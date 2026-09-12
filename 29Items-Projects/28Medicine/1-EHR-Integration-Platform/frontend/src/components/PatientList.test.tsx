/**
 * Smoke test for PatientList — verifies the search UI renders and the empty
 * state behaves before any query fires. Behavior-focused, not implementation.
 */
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { PatientList } from './PatientList';

function renderWithClient(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('PatientList', () => {
  it('renders the search input', () => {
    renderWithClient(<PatientList />);
    expect(screen.getByLabelText(/search by mrn/i)).toBeInTheDocument();
  });

  it('does not show loading state before a search is entered', () => {
    renderWithClient(<PatientList />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
