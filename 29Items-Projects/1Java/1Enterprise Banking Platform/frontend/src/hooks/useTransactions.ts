import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { transactionService } from '../services/transactionService';
import type { TransferRequest } from '../types/transaction';

const TRANSACTIONS_KEY = ['transactions'];

export function useAccountTransactions(accountId: string, page = 0, size = 20) {
  return useQuery({
    queryKey: [...TRANSACTIONS_KEY, 'account', accountId, page, size],
    queryFn: () => transactionService.getAccountTransactions(accountId, page, size),
    enabled: !!accountId,
  });
}

export function useTransaction(transactionId: string) {
  return useQuery({
    queryKey: [...TRANSACTIONS_KEY, transactionId],
    queryFn: () => transactionService.getTransaction(transactionId),
    enabled: !!transactionId,
  });
}

export function useTransfer() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: TransferRequest) =>
      transactionService.initiateTransfer(request),
    onSuccess: (_, variables) => {
      // Invalidate transactions for both accounts
      queryClient.invalidateQueries({
        queryKey: [...TRANSACTIONS_KEY, 'account', variables.sourceAccountId],
      });
      queryClient.invalidateQueries({
        queryKey: [...TRANSACTIONS_KEY, 'account', variables.targetAccountId],
      });
      // Invalidate account balances
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
    },
  });
}

export function useCancelTransaction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (transactionId: string) =>
      transactionService.cancelTransaction(transactionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TRANSACTIONS_KEY });
    },
  });
}
