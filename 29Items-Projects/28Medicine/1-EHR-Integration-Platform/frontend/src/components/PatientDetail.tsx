/**
 * PatientDetail — loads and displays a single patient by id.
 * Mirrors the loading/error/success pattern from PatientList for one resource.
 */
import { usePatient } from '@/hooks/usePatients';
import { displayName } from '@/types/fhir';

interface PatientDetailProps {
  patientId: string | null;
}

export function PatientDetail({ patientId }: PatientDetailProps): JSX.Element {
  const { data: patient, isLoading, isError, error } = usePatient(patientId);

  if (!patientId) {
    return <aside className="patient-detail">Select a patient to view details.</aside>;
  }
  if (isLoading) {
    return <aside className="patient-detail" role="status">Loading patient…</aside>;
  }
  if (isError || !patient) {
    return (
      <aside className="patient-detail" role="alert">
        Unable to load patient: {(error as Error)?.message ?? 'not found'}
      </aside>
    );
  }

  return (
    <aside className="patient-detail">
      <h2>{displayName(patient)}</h2>
      <dl>
        <dt>FHIR id</dt>
        <dd>{patient.id}</dd>
        <dt>Gender</dt>
        <dd>{patient.gender ?? 'unknown'}</dd>
        <dt>Date of birth</dt>
        <dd>{patient.birthDate ?? '—'}</dd>
        <dt>Identifier</dt>
        <dd>
          {patient.identifier?.[0]
            ? `${patient.identifier[0].system ?? ''}|${patient.identifier[0].value ?? ''}`
            : '—'}
        </dd>
      </dl>
      {/* TODO(P2-8): tabs for Encounters, Observations, and risk stratification. */}
    </aside>
  );
}
