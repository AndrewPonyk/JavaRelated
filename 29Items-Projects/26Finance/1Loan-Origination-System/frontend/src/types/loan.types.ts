export interface LoanApplication {
  id?: number;
  applicationId?: string;
  loanAmount: number;
  loanPurpose: string;
  loanTermMonths: number;
  applicantId: number;
  status?: string;
  creditScore?: number;
  debtToIncomeRatio?: number;
  loanToValueRatio?: number;
  createdAt?: string;
  updatedAt?: string;
  underwritingDecision?: string;
  decisionDate?: string;
}

export interface LoanApplicationFormData {
  loanAmount: number;
  loanPurpose: string;
  loanTermMonths: number;
  applicantId: number;
}

export enum ApplicationStatus {
  SUBMITTED = 'SUBMITTED',
  UNDER_REVIEW = 'UNDER_REVIEW',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
  MANUAL_REVIEW_REQUIRED = 'MANUAL_REVIEW_REQUIRED',
  FUNDED = 'FUNDED'
}

export interface UnderwritingDecision {
  decision: 'APPROVED' | 'REJECTED' | 'MANUAL_REVIEW';
  reason: string;
  conditions: string[];
}
