import api from './api';
import type { Account, CreateAccountRequest } from '../types/account';

const ACCOUNTS_ENDPOINT = '/accounts';

export interface DepositRequest {
  amount: number;
  currency: string;
  reference?: string;
}

export interface WithdrawRequest {
  amount: number;
  currency: string;
  reference?: string;
}

export const accountService = {
  /**
   * Get all accounts for the current user
   */
  async getAccounts(): Promise<Account[]> {
    const response = await api.get<Account[]>(ACCOUNTS_ENDPOINT);
    return response.data;
  },

  /**
   * Get account by ID
   */
  async getAccountById(accountId: string): Promise<Account> {
    const response = await api.get<Account>(`${ACCOUNTS_ENDPOINT}/${accountId}`);
    return response.data;
  },

  /**
   * Get account by account number
   */
  async getAccountByNumber(accountNumber: string): Promise<Account> {
    const response = await api.get<Account>(
      `${ACCOUNTS_ENDPOINT}/number/${accountNumber}`
    );
    return response.data;
  },

  /**
   * Create a new account
   */
  async createAccount(request: CreateAccountRequest): Promise<Account> {
    const response = await api.post<Account>(ACCOUNTS_ENDPOINT, request);
    return response.data;
  },

  /**
   * Get account balance
   */
  async getBalance(accountId: string): Promise<number> {
    const response = await api.get<number>(`${ACCOUNTS_ENDPOINT}/${accountId}/balance`);
    return response.data;
  },

  /**
   * Deposit funds to an account
   */
  async deposit(accountId: string, request: DepositRequest): Promise<Account> {
    const response = await api.post<Account>(
      `${ACCOUNTS_ENDPOINT}/${accountId}/deposit`,
      request
    );
    return response.data;
  },

  /**
   * Withdraw funds from an account
   */
  async withdraw(accountId: string, request: WithdrawRequest): Promise<Account> {
    const response = await api.post<Account>(
      `${ACCOUNTS_ENDPOINT}/${accountId}/withdraw`,
      request
    );
    return response.data;
  },

  /**
   * Freeze an account
   */
  async freezeAccount(accountId: string): Promise<Account> {
    const response = await api.post<Account>(`${ACCOUNTS_ENDPOINT}/${accountId}/freeze`);
    return response.data;
  },

  /**
   * Unfreeze an account
   */
  async unfreezeAccount(accountId: string): Promise<Account> {
    const response = await api.post<Account>(`${ACCOUNTS_ENDPOINT}/${accountId}/unfreeze`);
    return response.data;
  },

  /**
   * Close an account
   */
  async closeAccount(accountId: string): Promise<void> {
    await api.delete(`${ACCOUNTS_ENDPOINT}/${accountId}`);
  },
};
