/**
 * PatientList — searches patients by identifier and renders results.
 *
 * Demonstrates the standard data-fetching component pattern:
 *   1. fetch via a typed hook (TanStack Query)
 *   2. explicit loading / error / empty / success states
 *   3. a pure display layer driven by typed FHIR models
 */
import { useState } from 'react';
import { usePatientSearch } from '@/hooks/usePatients';
import { displayName, type Patient } from '@/types/fhir';

interface PatientListProps {
  onSelect?: (patient: Patient) => void;
}

export function PatientList({ onSelect }: PatientListProps): JSX.Element {
  const [identifier, setIdentifier] = useState('');
  const { data: patients, isLoading, isError, error, isFetched } = usePatientSearch(identifier);

  return (
    <section className="patient-list" aria-label="Patient search">
      <form
        onSubmit={(e) => e.preventDefault()}
        role="search"
        className="patient-list__search"
      >
        <label htmlFor="identifier">Search by MRN / identifier</label>
        <input
          id="identifier"
          type="text"
          value={identifier}
          placeholder="e.g. urn:oid:1.2.3|12345"
          onChange={(e) => setIdentifier(e.target.value)}
          autoComplete="off"
        />
      </form>

      {/* --- state handling --- */}
      {isLoading && <p role="status">Searching…</p>}

      {isError && (
        <p role="alert" className="patient-list__error">
          Could not load patients: {(error as Error).message}
        </p>
      )}

      {isFetched && !isLoading && !isError && (patients?.length ?? 0) === 0 && (
        <p>No patients match “{identifier}”.</p>
      )}

      {!!patients?.length && (
        <ul className="patient-list__results">
          {patients.map((patient) => (
            <li key={patient.id}>
              <button type="button" onClick={() => onSelect?.(patient)}>
                <span className="patient-list__name">{displayName(patient)}</span>
                <span className="patient-list__meta">
                  {patient.gender ?? 'unknown'} · DOB {patient.birthDate ?? '—'}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
