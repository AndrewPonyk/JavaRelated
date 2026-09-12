/** Patient API calls — FHIR for reads/search, REST admin for registration. */
import { apiPost, fhirGet } from '@/api/client';
import type { Bundle, Patient } from '@/types/fhir';
import type { PatientRegistration, PatientSummary } from '@/types/api';

/** Search patients by identifier (system|value or just value) via FHIR. */
export async function searchPatients(identifier: string): Promise<Patient[]> {
  const query = encodeURIComponent(identifier);
  const bundle = await fhirGet<Bundle<Patient>>(`/Patient?identifier=${query}`);
  return bundle.entry?.map((e) => e.resource) ?? [];
}

/** Search patients by family name via FHIR. */
export async function searchPatientsByFamily(family: string): Promise<Patient[]> {
  const query = encodeURIComponent(family);
  const bundle = await fhirGet<Bundle<Patient>>(`/Patient?family=${query}`);
  return bundle.entry?.map((e) => e.resource) ?? [];
}

/** Read a single patient by logical id via FHIR. */
export async function getPatient(id: string): Promise<Patient> {
  return fhirGet<Patient>(`/Patient/${encodeURIComponent(id)}`);
}

/** Register a patient via the REST admin API (validated, returns a summary). */
export async function registerPatient(request: PatientRegistration): Promise<PatientSummary> {
  return apiPost<PatientSummary>('/patients', request);
}
