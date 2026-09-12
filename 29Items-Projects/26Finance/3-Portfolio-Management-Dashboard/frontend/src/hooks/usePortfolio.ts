// Small data-fetching hooks that expose explicit loading / error / data states.
// (A production app would likely adopt TanStack Query for caching + retries.)

import { useEffect, useState } from "react";
import { api } from "../api/client";
import type {
  AttributionResponse,
  FrontierResponse,
  Portfolio,
  RiskMetrics,
} from "../types/portfolio";

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
}

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    fn()
      .then((result) => {
        if (active) {
          setData(result);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (active) setError(err instanceof Error ? err.message : "Error");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  const reload = () => setNonce((n) => n + 1);
  return { data, loading, error, reload };
}

export const usePortfolios = () => useAsync<Portfolio[]>(() => api.listPortfolios(), []);

export const usePortfolioDetail = (id: number) =>
  useAsync<Portfolio>(() => api.getPortfolio(id), [id]);

export const useEfficientFrontier = (id: number) =>
  useAsync<FrontierResponse>(() => api.getFrontier(id, { n_points: 50 }), [id]);

export const useRiskMetrics = (id: number) =>
  useAsync<RiskMetrics>(() => api.getRisk(id), [id]);

export const useAttribution = (id: number) =>
  useAsync<AttributionResponse>(() => api.getAttribution(id), [id]);
