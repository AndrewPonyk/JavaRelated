import { useCallback, useEffect, useState } from "react";

import { ApiError, fetchJson } from "../api/client";

export interface ApiQuery<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

/** GET a JSON resource with loading/error state, cancellation on unmount,
 *  and manual refresh. Pass `null` to keep the query idle. */
export function useApiQuery<T>(path: string | null): ApiQuery<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(path !== null);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const refresh = useCallback(() => setRefreshKey((key) => key + 1), []);

  useEffect(() => {
    if (path === null) {
      setData(null);
      setLoading(false);
      setError(null);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError(null);

    fetchJson<T>(path, { signal: controller.signal })
      .then((result) => setData(result))
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(err instanceof ApiError ? err.message : "Catalog API is unreachable");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [path, refreshKey]);

  return { data, loading, error, refresh };
}
