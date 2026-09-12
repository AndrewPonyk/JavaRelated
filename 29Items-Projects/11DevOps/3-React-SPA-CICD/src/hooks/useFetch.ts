import { useCallback, useEffect, useRef, useState } from 'react';
import type { ZodType } from 'zod';

import { api, ApiError } from '@/api/httpClient';

export type FetchStatus = 'idle' | 'loading' | 'success' | 'error';

export interface UseFetchResult<T> {
  data: T | null;
  error: ApiError | null;
  status: FetchStatus;
  refetch: () => void;
}

interface UseFetchOptions<T> {
  schema?: ZodType<T>;
}

/**
 * Minimal abortable GET hook with an explicit status machine — every consumer renders
 * loading/error/empty as first-class states (see ExampleComponent for the reference usage).
 *
 * Deliberately small: no caching, no deduplication. When those needs appear, migrate the
 * call sites to TanStack Query (hook point noted in src/app/providers.tsx) rather than
 * growing this hook.
 */
export function useFetch<T>(path: string, options: UseFetchOptions<T> = {}): UseFetchResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [status, setStatus] = useState<FetchStatus>('idle');
  const [nonce, setNonce] = useState(0);

  // Kept in a ref so an inline `{ schema }` object doesn't retrigger the effect every
  // render (infinite refetch loop). Consequence: schema changes don't refetch — only
  // `path` and `refetch()` do, which is the intended contract.
  const schemaRef = useRef(options.schema);
  schemaRef.current = options.schema;

  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');

    api
      .get<T>(path, { signal: controller.signal, schema: schemaRef.current })
      .then((result) => {
        setData(result);
        setError(null);
        setStatus('success');
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return; // unmount/re-run — not an error state
        setError(err instanceof ApiError ? err : new ApiError(0, 'UNKNOWN', String(err)));
        setStatus('error');
      });

    return () => controller.abort();
  }, [path, nonce]);

  const refetch = useCallback(() => setNonce((n) => n + 1), []);

  return { data, error, status, refetch };
}
