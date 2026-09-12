import { useState, useEffect, useCallback } from 'react';
import { fetchUsageMetrics, fetchCapacityPrediction, fetchInvoicePreview } from '../api/usage';
import { UsageMetrics, CapacityPrediction, InvoicePreview } from '../types/usage';

export function useUsageMetrics(metric: string = 'api_calls') {
  const [metrics, setMetrics] = useState<UsageMetrics | null>(null);
  const [prediction, setPrediction] = useState<CapacityPrediction | null>(null);
  const [invoice, setInvoice] = useState<InvoicePreview | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [m, p, inv] = await Promise.all([
        fetchUsageMetrics(metric),
        fetchCapacityPrediction(metric),
        fetchInvoicePreview(),
      ]);
      setMetrics(m);
      setPrediction(p);
      setInvoice(inv);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to fetch usage metrics';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, [metric]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  return {
    metrics,
    prediction,
    invoice,
    isLoading,
    error,
    refetch: loadData,
  };
}
