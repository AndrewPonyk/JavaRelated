export type TransactionType =
  | 'INTERNAL_TRANSFER'
  | 'EXTERNAL_TRANSFER'
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'PAYMENT'
  | 'LOAN_DISBURSEMENT'
  | 'LOAN_REPAYMENT'
  | 'INTEREST_CREDIT'
  | 'FEE_DEDUCTION';

export type TransactionStatus =
  | 'INITIATED'
  | 'PENDING_FRAUD_CHECK'
  | 'PENDING_REVIEW'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'REVERSED'
  | 'CANCELLED';

export interface Transaction {
  id: string;
  referenceNumber: string;
  sourceAccountId: string;
  targetAccountId: string;
  transactionType: TransactionType;
  amount: number;
  currency: string;
  description?: string;
  status: TransactionStatus;
  initiatedAt: string;
  completedAt?: string;
  failureReason?: string;
}

export interface TransferRequest {
  sourceAccountId: string;
  targetAccountId: string;
  transactionType: TransactionType;
  amount: number;
  currency: string;
  description?: string;
}
