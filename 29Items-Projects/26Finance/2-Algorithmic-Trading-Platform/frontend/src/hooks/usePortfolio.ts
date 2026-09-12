// Polling hooks for live positions and PnL. Refresh on an interval so the
// dashboard reflects the trading loop without manual reloads.

import { useCallback, useEffect, useState } from 'react';

import { ApiError, portfolioApi } from '../services/api';
import type { PnLSummary, Position } from '../types/strategy';

const POLL_MS = 2000;

export function usePortfolio() {
  const [positions, setPositions] = useState<Position[]>([]);
  const [pnl, setPnl] = useState<PnLSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [pos, summary] = await Promise.all([portfolioApi.positions(), portfolioApi.pnl()]);
      setPositions(pos);
      setPnl(summary);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unexpected error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const id = setInterval(() => void load(), POLL_MS);
    return () => clearInterval(id);
  }, [load]);

  return { positions, pnl, loading, error, refetch: load };
}
