import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { loanService } from '../services/loanService';
import type { LoanApplicationRequest } from '../types/loan';

const APPLICATIONS_KEY = ['loan-applications'];
const LOANS_KEY = ['loans'];

export function useMyLoanApplications() {
  return useQuery({
    queryKey: [...APPLICATIONS_KEY, 'my'],
    queryFn: loanService.getMyApplications,
  });
}

export function useLoanApplication(applicationId: string) {
  return useQuery({
    queryKey: [...APPLICATIONS_KEY, applicationId],
    queryFn: () => loanService.getApplication(applicationId),
    enabled: !!applicationId,
  });
}

export function useCreateLoanApplication() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: LoanApplicationRequest) =>
      loanService.createApplication(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: APPLICATIONS_KEY });
    },
  });
}

export function useCancelLoanApplication() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (applicationId: string) =>
      loanService.cancelApplication(applicationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: APPLICATIONS_KEY });
    },
  });
}

export function useLoanApplicationStatistics() {
  return useQuery({
    queryKey: [...APPLICATIONS_KEY, 'statistics'],
    queryFn: loanService.getApplicationStatistics,
  });
}

export function useMyLoans() {
  return useQuery({
    queryKey: [...LOANS_KEY, 'my'],
    queryFn: loanService.getMyLoans,
  });
}

export function useLoan(loanId: string) {
  return useQuery({
    queryKey: [...LOANS_KEY, loanId],
    queryFn: () => loanService.getLoan(loanId),
    enabled: !!loanId,
  });
}

export function useLoanPayments(loanId: string) {
  return useQuery({
    queryKey: [...LOANS_KEY, loanId, 'payments'],
    queryFn: () => loanService.getLoanPayments(loanId),
    enabled: !!loanId,
  });
}

export function useMakePayment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ loanId, amount }: { loanId: string; amount: number }) =>
      loanService.makePayment(loanId, amount),
    onSuccess: (_, { loanId }) => {
      queryClient.invalidateQueries({ queryKey: [...LOANS_KEY, loanId] });
      queryClient.invalidateQueries({ queryKey: [...LOANS_KEY, loanId, 'payments'] });
    },
  });
}
