import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { accountService, DepositRequest, WithdrawRequest } from '../services/accountService';
import type { CreateAccountRequest } from '../types/account';

const ACCOUNTS_KEY = ['accounts'];

export function useAccounts() {
  return useQuery({
    queryKey: ACCOUNTS_KEY,
    queryFn: accountService.getAccounts,
  });
}

export function useAccount(accountId: string) {
  return useQuery({
    queryKey: [...ACCOUNTS_KEY, accountId],
    queryFn: () => accountService.getAccountById(accountId),
    enabled: !!accountId,
  });
}

export function useAccountBalance(accountId: string) {
  return useQuery({
    queryKey: [...ACCOUNTS_KEY, accountId, 'balance'],
    queryFn: () => accountService.getBalance(accountId),
    enabled: !!accountId,
    refetchInterval: 30000,
  });
}

export function useCreateAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateAccountRequest) =>
      accountService.createAccount(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ACCOUNTS_KEY });
    },
  });
}

export function useDeposit() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ accountId, request }: { accountId: string; request: DepositRequest }) =>
      accountService.deposit(accountId, request),
    onSuccess: (_, { accountId }) => {
      queryClient.invalidateQueries({ queryKey: [...ACCOUNTS_KEY, accountId] });
      queryClient.invalidateQueries({ queryKey: ACCOUNTS_KEY });
    },
  });
}

export function useWithdraw() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ accountId, request }: { accountId: string; request: WithdrawRequest }) =>
      accountService.withdraw(accountId, request),
    onSuccess: (_, { accountId }) => {
      queryClient.invalidateQueries({ queryKey: [...ACCOUNTS_KEY, accountId] });
      queryClient.invalidateQueries({ queryKey: ACCOUNTS_KEY });
    },
  });
}

export function useFreezeAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (accountId: string) => accountService.freezeAccount(accountId),
    onSuccess: (_, accountId) => {
      queryClient.invalidateQueries({ queryKey: [...ACCOUNTS_KEY, accountId] });
      queryClient.invalidateQueries({ queryKey: ACCOUNTS_KEY });
    },
  });
}

export function useUnfreezeAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (accountId: string) => accountService.unfreezeAccount(accountId),
    onSuccess: (_, accountId) => {
      queryClient.invalidateQueries({ queryKey: [...ACCOUNTS_KEY, accountId] });
      queryClient.invalidateQueries({ queryKey: ACCOUNTS_KEY });
    },
  });
}

export function useCloseAccount() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (accountId: string) => accountService.closeAccount(accountId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ACCOUNTS_KEY });
    },
  });
}
