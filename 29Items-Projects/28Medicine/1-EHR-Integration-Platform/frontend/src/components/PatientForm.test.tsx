import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PatientForm } from './PatientForm';

function renderWithClient(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('PatientForm', () => {
  afterEach(() => vi.restoreAllMocks());

  it('shows validation errors and does not submit when required fields are empty', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    renderWithClient(<PatientForm />);

    fireEvent.click(screen.getByRole('button', { name: /register/i }));

    expect(await screen.findByText(/family name is required/i)).toBeInTheDocument();
    expect(screen.getByText(/given name is required/i)).toBeInTheDocument();
    expect(screen.getByText(/mrn is required/i)).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('submits to the API when valid and shows success', async () => {
    const summary = {
      id: 'p1',
      identifierSystem: 'urn:mrn',
      identifierValue: 'M1',
      familyName: 'Doe',
      givenName: 'Jane',
      birthDate: null,
      gender: null,
    };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => summary }),
    );

    renderWithClient(<PatientForm />);
    fireEvent.change(screen.getByLabelText(/mrn \/ identifier/i), { target: { value: 'M1' } });
    fireEvent.change(screen.getByLabelText(/family name/i), { target: { value: 'Doe' } });
    fireEvent.change(screen.getByLabelText(/given name/i), { target: { value: 'Jane' } });

    fireEvent.click(screen.getByRole('button', { name: /register/i }));

    expect(await screen.findByText(/registered patient p1/i)).toBeInTheDocument();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/patients',
      expect.objectContaining({ method: 'POST' }),
    );
  });
});
