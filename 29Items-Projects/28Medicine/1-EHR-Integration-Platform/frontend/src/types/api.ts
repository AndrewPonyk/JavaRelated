/** DTOs for the non-FHIR REST + ML APIs (mirror the backend records). */

export interface PatientRegistration {
  identifierSystem: string;
  identifierValue: string;
  familyName: string;
  givenName: string;
  birthDate?: string; // yyyy-MM-dd
  gender?: string;
}

export interface PatientSummary {
  id: string;
  identifierSystem: string | null;
  identifierValue: string | null;
  familyName: string | null;
  givenName: string | null;
  birthDate: string | null;
  gender: string | null;
}

export interface StratificationRequest {
  patientId: string;
  noteText: string;
  topK: number;
}

export interface StratificationResponse {
  patientId: string;
  riskScore: number;
  riskTier: 'LOW' | 'MODERATE' | 'HIGH';
  cohort: string[];
}
