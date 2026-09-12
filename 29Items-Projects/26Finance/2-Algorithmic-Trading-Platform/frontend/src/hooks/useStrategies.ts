// Data-fetching hook for strategies. Encapsulates loading/error/refetch state
// so components stay declarative. (Phase 2: swap for TanStack Query for caching,
// retries, and websocket-driven invalidation.)

import { useCallback, useEffect, useState } from 'react';

import { ApiError, strategiesApi } from '../services/api';
import type { CreateStrategyRequest, Strategy } from '../types/strategy';

interface UseStrategiesResult {
  strategies: Strategy[];
  loading: boolean;
  error: string | null;
  refetch: () => void;
  halt: (id: string) => Promise<void>;
  create: (body: CreateStrategyRequest) => Promise<Strategy>;
  remove: (id: string) => Promise<void>;
}

export function useStrategies(): UseStrategiesResult {
  const [strategies, setStrategies] = useState<Strategy[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setStrategies(await strategiesApi.list());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unexpected error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const halt = useCallback(
    async (id: string) => {
      const updated = await strategiesApi.halt(id);
      setStrategies((prev) => prev.map((s) => (s.id === id ? updated : s)));
    },
    [],
  );

  const create = useCallback(async (body: CreateStrategyRequest) => {
    const created = await strategiesApi.create(body);
    setStrategies((prev) => [...prev, created]);
    return created;
  }, []);

  const remove = useCallback(async (id: string) => {
    await strategiesApi.remove(id);
    setStrategies((prev) => prev.filter((s) => s.id !== id));
  }, []);

  return { strategies, loading, error, refetch: load, halt, create, remove };
}
