import { dashboardSummarySchema, type DashboardSummary } from './dashboard.api';
import { useFetch, type UseFetchResult } from '@/hooks/useFetch';

export function useDashboardData(): UseFetchResult<DashboardSummary> {
  return useFetch<DashboardSummary>('/v1/dashboard/summary', {
    schema: dashboardSummarySchema,
  });
}
