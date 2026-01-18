/**
 * Healthcare Claims Processing - Patient Models
 */

export interface Patient {
  id: string;
  memberId: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  policyNumber: string;
  groupNumber?: string;
  email?: string;
  phone?: string;
  isActive: boolean;
}

export function getFullName(patient: Patient): string {
  return `${patient.firstName} ${patient.lastName}`;
}

export function getAge(patient: Patient): number {
  const birthDate = new Date(patient.dateOfBirth);
  const today = new Date();
  let age = today.getFullYear() - birthDate.getFullYear();
  const monthDiff = today.getMonth() - birthDate.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
    age--;
  }
  return age;
}
