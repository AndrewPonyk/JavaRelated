/**
 * Healthcare Claims Processing - Claim Models
 */

export type ClaimStatus =
  | 'SUBMITTED'
  | 'VALIDATING'
  | 'INVALID'
  | 'PENDING_ADJUDICATION'
  | 'AUTO_ADJUDICATED'
  | 'PENDING_REVIEW'
  | 'FLAGGED_FRAUD'
  | 'APPROVED'
  | 'DENIED'
  | 'PAID';

export type ClaimType =
  | 'MEDICAL'
  | 'DENTAL'
  | 'VISION'
  | 'PHARMACY'
  | 'MENTAL_HEALTH'
  | 'REHABILITATION'
  | 'DURABLE_MEDICAL_EQUIPMENT'
  | 'LABORATORY'
  | 'RADIOLOGY'
  | 'EMERGENCY'
  | 'INPATIENT'
  | 'OUTPATIENT';

export interface Claim {
  id: string;
  claimNumber: string;
  type: ClaimType;
  status: ClaimStatus;
  amount: number;
  allowedAmount?: number;
  serviceDate: string;
  serviceEndDate?: string;
  patientId: string;
  providerId: string;
  diagnosisCodes?: string;
  procedureCodes?: string;
  fraudScore?: number;
  fraudReasons?: string;
  denialReason?: string;
  notes?: string;
  submittedBy?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ClaimInput {
  type: ClaimType;
  amount: number;
  serviceDate: string;
  serviceEndDate?: string;
  patientId: string;
  providerId: string;
  diagnosisCodes?: string;
  procedureCodes?: string;
  notes?: string;
}

export interface ClaimFilter {
  status?: ClaimStatus;
  patientId?: string;
  providerId?: string;
}

export interface Pagination {
  page: number;
  size: number;
}

export interface PageInfo {
  currentPage: number;
  pageSize: number;
  hasNextPage: boolean;
}

export interface ClaimConnection {
  edges: Claim[];
  pageInfo: PageInfo;
}

export interface FraudScore {
  claimId: string;
  claimNumber: string;
  score: number;
  riskLevel: 'MINIMAL' | 'LOW' | 'MEDIUM' | 'HIGH';
  reasons: string[];
  requiresReview: boolean;
  likelyFraud: boolean;
}

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  VALIDATING: 'Validating',
  INVALID: 'Invalid',
  PENDING_ADJUDICATION: 'Pending Adjudication',
  AUTO_ADJUDICATED: 'Auto-Adjudicated',
  PENDING_REVIEW: 'Pending Review',
  FLAGGED_FRAUD: 'Fraud Flagged',
  APPROVED: 'Approved',
  DENIED: 'Denied',
  PAID: 'Paid',
};

export const CLAIM_TYPE_LABELS: Record<ClaimType, string> = {
  MEDICAL: 'Medical',
  DENTAL: 'Dental',
  VISION: 'Vision',
  PHARMACY: 'Pharmacy',
  MENTAL_HEALTH: 'Mental Health',
  REHABILITATION: 'Rehabilitation',
  DURABLE_MEDICAL_EQUIPMENT: 'Durable Medical Equipment',
  LABORATORY: 'Laboratory',
  RADIOLOGY: 'Radiology',
  EMERGENCY: 'Emergency',
  INPATIENT: 'Inpatient',
  OUTPATIENT: 'Outpatient',
};

export function getStatusBadgeClass(status: ClaimStatus): string {
  switch (status) {
    case 'SUBMITTED':
    case 'VALIDATING':
      return 'badge-submitted';
    case 'PENDING_ADJUDICATION':
    case 'PENDING_REVIEW':
      return 'badge-pending';
    case 'APPROVED':
    case 'PAID':
      return 'badge-approved';
    case 'DENIED':
    case 'INVALID':
      return 'badge-denied';
    case 'FLAGGED_FRAUD':
      return 'badge-fraud';
    default:
      return 'badge-submitted';
  }
}
