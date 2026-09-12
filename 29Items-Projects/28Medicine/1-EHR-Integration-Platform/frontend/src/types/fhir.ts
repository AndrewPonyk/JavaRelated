/**
 * Minimal FHIR R4 TypeScript models used by the console.
 *
 * Only the fields the UI consumes are typed here; extend as features grow.
 * Modeling these explicitly (with `strict` TS) prevents silent `undefined`
 * bugs in clinical screens.
 */

export interface HumanName {
  family?: string;
  given?: string[];
  text?: string;
}

export interface Identifier {
  system?: string;
  value?: string;
}

export type AdministrativeGender = 'male' | 'female' | 'other' | 'unknown';

export interface Patient {
  resourceType: 'Patient';
  id: string;
  identifier?: Identifier[];
  name?: HumanName[];
  gender?: AdministrativeGender;
  birthDate?: string; // ISO date
}

/** A FHIR searchset Bundle (the result of a search interaction). */
export interface Bundle<T> {
  resourceType: 'Bundle';
  type: string;
  total?: number;
  entry?: Array<{ resource: T }>;
}

/** FHIR error payload returned on failed interactions. */
export interface OperationOutcome {
  resourceType: 'OperationOutcome';
  issue: Array<{
    severity: 'fatal' | 'error' | 'warning' | 'information';
    code: string;
    diagnostics?: string;
  }>;
}

/** Convenience: render a patient's display name from FHIR name parts. */
export function displayName(patient: Patient): string {
  const name = patient.name?.[0];
  if (!name) return '(unnamed)';
  if (name.text) return name.text;
  return [name.given?.join(' '), name.family].filter(Boolean).join(' ') || '(unnamed)';
}
