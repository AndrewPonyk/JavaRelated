import api from './api';
import type { Transaction, TransferRequest } from '../types/transaction';

const TRANSACTIONS_ENDPOINT = '/transactions';

export const transactionService = {
  /**
   * Initiate a fund transfer
   */
  async initiateTransfer(request: TransferRequest): Promise<Transaction> {
    const response = await api.post<Transaction>(
      `${TRANSACTIONS_ENDPOINT}/transfer`,
      request
    );
    return response.data;
  },

  /**
   * Get transaction by ID
   */
  async getTransaction(transactionId: string): Promise<Transaction> {
    const response = await api.get<Transaction>(
      `${TRANSACTIONS_ENDPOINT}/${transactionId}`
    );
    return response.data;
  },

  /**
   * Get transactions for an account
   */
  async getAccountTransactions(
    accountId: string,
    page = 0,
    size = 20
  ): Promise<Transaction[]> {
    const response = await api.get<Transaction[]>(
      `${TRANSACTIONS_ENDPOINT}/account/${accountId}`,
      {
        params: { page, size },
      }
    );
    return response.data;
  },

  /**
   * Cancel a pending transaction
   */
  async cancelTransaction(transactionId: string): Promise<Transaction> {
    const response = await api.post<Transaction>(
      `${TRANSACTIONS_ENDPOINT}/${transactionId}/cancel`
    );
    return response.data;
  },
};
