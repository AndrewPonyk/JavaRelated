/** Polling data hooks with loading/error state.
 *  The /events SSE endpoint exists server-side; polling keeps the client
 *  dependency-free and is fine at dashboard refresh rates. */

import { useCallback, useEffect, useState } from "react";
import { api } from "../api/client";
import type { Anomaly, MetricSeries } from "../types";

export interface Polled<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
  refresh: () => void;
}

export function usePolling<T>(
  fetcher: () => Promise<T>,
  intervalMs: number,
  deps: unknown[],
): Polled<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  const refresh = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;

    const run = async () => {
      try {
        const result = await fetcher();
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err));
      } finally {
        if (!cancelled) {
          setLoading(false);
          timer = window.setTimeout(run, intervalMs);
        }
      }
    };

    setLoading(true);
    void run();
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);

  return { data, error, loading, refresh };
}

export function useMetrics(
  deviceId: string | null,
  metric: string,
  hours = 6,
): Polled<MetricSeries> {
  return usePolling<MetricSeries>(
    () =>
      deviceId
        ? api.getSeries(deviceId, metric, hours)
        : Promise.reject(new Error("No device selected")),
    30_000,
    [deviceId, metric, hours],
  );
}

export function useAnomalies(deviceId: string | null): Polled<Anomaly[]> {
  return usePolling<Anomaly[]>(
    () =>
      deviceId ? api.listAnomalies(deviceId) : Promise.reject(new Error("No device selected")),
    60_000,
    [deviceId],
  );
}

export function useMetricCatalog(deviceId: string | null): Polled<string[]> {
  return usePolling<string[]>(
    () =>
      deviceId ? api.getMetricCatalog(deviceId) : Promise.reject(new Error("No device selected")),
    300_000,
    [deviceId],
  );
}
