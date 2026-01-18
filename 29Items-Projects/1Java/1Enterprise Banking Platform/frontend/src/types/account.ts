export type AccountType =
  | 'CHECKING'
  | 'SAVINGS'
  | 'BUSINESS'
  | 'MONEY_MARKET'
  | 'CERTIFICATE_OF_DEPOSIT';

export type AccountStatus =
  | 'PENDING_ACTIVATION'
  | 'ACTIVE'
  | 'FROZEN'
  | 'DORMANT'
  | 'CLOSED';

export interface Account {
  id: string;
  accountNumber: string;
  ownerName: string;
  ownerEmail: string;
  accountType: AccountType;
  currency: string;
  balance: number;
  status: AccountStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  ownerName: string;
  ownerEmail: string;
  accountType: AccountType;
  currency: string;
  initialDeposit?: number;
}
