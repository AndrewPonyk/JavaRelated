import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import loanService from '../services/loanService';
import { LoanApplicationFormData } from '../types/loan.types';

export const useLoanApplication = (id?: number) => {
  const queryClient = useQueryClient();

  const { data: application, isLoading, error } = useQuery({
    queryKey: ['loanApplication', id],
    queryFn: () => loanService.getApplication(id!),
    enabled: !!id
  });

  const submitMutation = useMutation({
    mutationFn: (data: LoanApplicationFormData) => loanService.submitApplication(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loanApplications'] });
    },
    onError: (err: Error) => {
      // Error is surfaced via submitMutation.error so callers can display it
      console.error('Loan application submission failed:', err.message);
    }
  });

  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      loanService.updateApplicationStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loanApplications'] });
      queryClient.invalidateQueries({ queryKey: ['loanApplication', id] });
    },
    onError: (err: Error) => {
      console.error('Status update failed:', err.message);
    }
  });

  return {
    application,
    isLoading,
    error,
    submitApplication: submitMutation.mutate,
    updateStatus: updateStatusMutation.mutate,
    isSubmitting: submitMutation.isPending,
    submitError: submitMutation.error as Error | null
  };
};

export const useAllLoanApplications = (status?: string) => {
  return useQuery({
    queryKey: ['loanApplications', status],
    queryFn: () => loanService.getAllApplications(status)
  });
};
