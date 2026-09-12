import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { invoicesApi, type SubmitInvoicePayload } from '@/api/invoicesApi';

/**
 * TanStack Query hooks encapsulate server state — caching, retries, background refetch,
 * and invalidation — so components don't hand-roll `useEffect` fetching (TECH-NOTES §3.6).
 */

const keys = {
  all: ['invoices'] as const,
  list: (companyId: string) => [...keys.all, 'list', companyId] as const,
  detail: (id: string) => [...keys.all, 'detail', id] as const,
};

export function useCompanyInvoices(companyId: string) {
  return useQuery({
    queryKey: keys.list(companyId),
    queryFn: () => invoicesApi.list(companyId),
    enabled: Boolean(companyId),
  });
}

export function useInvoice(id: string, options?: { pollWhilePending?: boolean }) {
  return useQuery({
    queryKey: keys.detail(id),
    queryFn: () => invoicesApi.getById(id),
    enabled: Boolean(id),
    // While underwriting runs asynchronously, poll until the offer/decision lands.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      const pending = status === 'Submitted' || status === 'UnderReview';
      return options?.pollWhilePending && pending ? 3_000 : false;
    },
  });
}

export function useSubmitInvoice(companyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: SubmitInvoicePayload) => invoicesApi.submit(payload),
    onSuccess: () => {
      // Refresh the list once a new invoice is accepted for underwriting.
      void queryClient.invalidateQueries({ queryKey: keys.list(companyId) });
    },
  });
}

export function useAcceptOffer(companyId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ invoiceId, idempotencyKey }: { invoiceId: string; idempotencyKey: string }) =>
      invoicesApi.acceptOffer(invoiceId, idempotencyKey),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: keys.detail(variables.invoiceId) });
      void queryClient.invalidateQueries({ queryKey: keys.list(companyId) });
    },
  });
}
