export type LoanType =
  | 'PERSONAL'
  | 'MORTGAGE'
  | 'AUTO'
  | 'BUSINESS'
  | 'HOME_EQUITY'
  | 'STUDENT';

export type EmploymentStatus =
  | 'EMPLOYED'
  | 'SELF_EMPLOYED'
  | 'UNEMPLOYED'
  | 'RETIRED'
  | 'STUDENT';

export type ApplicationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'DOCUMENTS_REQUIRED'
  | 'APPROVED'
  | 'REJECTED'
  | 'DISBURSED'
  | 'CANCELLED'
  | 'CLOSED';

export type LoanStatus =
  | 'PENDING_DISBURSEMENT'
  | 'ACTIVE'
  | 'DELINQUENT'
  | 'DEFAULTED'
  | 'PAID_OFF'
  | 'CANCELLED';

export type PaymentStatus =
  | 'SCHEDULED'
  | 'PAID'
  | 'LATE'
  | 'MISSED'
  | 'WAIVED';

export interface LoanApplication {
  id: string;
  applicationNumber: string;
  applicantId: string;
  applicantName: string;
  applicantEmail: string;
  loanType: LoanType;
  requestedAmount: number;
  currency: string;
  termMonths: number;
  annualIncome: number;
  employmentStatus: EmploymentStatus;
  purpose: string;
  status: ApplicationStatus;
  creditScore?: number;
  approvedAmount?: number;
  interestRate?: number;
  rejectionReason?: string;
  reviewedBy?: string;
  approvedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Loan {
  id: string;
  loanNumber: string;
  applicationId: string;
  borrowerId: string;
  borrowerName: string;
  loanType: LoanType;
  principalAmount: number;
  outstandingBalance: number;
  interestRate: number;
  termMonths: number;
  monthlyPayment: number;
  currency: string;
  status: LoanStatus;
  disbursementAccountId: string;
  disbursedAt?: string;
  maturityDate?: string;
  nextPaymentDate?: string;
  nextPaymentAmount?: number;
  createdAt: string;
}

export interface LoanPayment {
  id: string;
  loanId: string;
  paymentNumber: number;
  dueDate: string;
  principalAmount: number;
  interestAmount: number;
  totalAmount: number;
  status: PaymentStatus;
  paidAmount?: number;
  paidAt?: string;
}

export interface LoanApplicationRequest {
  applicantName: string;
  applicantEmail: string;
  loanType: LoanType;
  requestedAmount: number;
  currency: string;
  termMonths: number;
  annualIncome: number;
  employmentStatus: EmploymentStatus;
  purpose: string;
}

export interface LoanStatistics {
  submitted: number;
  underReview: number;
  approved: number;
  rejected: number;
  disbursed: number;
}
