import { useEffect, useState } from 'react';
import { API_BASE } from '../api/client';
import type { LiveAggregate, StreamStatus } from '../types/metrics';

export interface MetricsStream {
  status: StreamStatus;
  /** Latest aggregate pushed over SSE (one record per metric series). */
  lastAggregate: LiveAggregate | null;
  /** Increments on every new alert — panels refetch when it changes. */
  alertsVersion: number;
}

/**
 * Subscribes to the API's SSE stream (/api/v1/stream/metrics).
 *
 * EventSource auto-reconnects; while it does, status is 'paused' and the UI shows
 * a "live paused" pill instead of silently stale numbers (ARCHITECTURE.md §2.6).
 */
export function useMetricsStream(): MetricsStream {
  const [status, setStatus] = useState<StreamStatus>('connecting');
  const [lastAggregate, setLastAggregate] = useState<LiveAggregate | null>(null);
  const [alertsVersion, setAlertsVersion] = useState(0);

  useEffect(() => {
    const source = new EventSource(`${API_BASE}/api/v1/stream/metrics`);

    source.onopen = () => setStatus('live');
    source.onerror = () => setStatus('paused'); // built-in retry kicks in
    source.addEventListener('connected', () => setStatus('live'));
    source.addEventListener('heartbeat', () => setStatus('live'));
    source.addEventListener('aggregate', (event) => {
      setStatus('live');
      try {
        setLastAggregate(JSON.parse((event as MessageEvent).data) as LiveAggregate);
      } catch {
        /* malformed push — ignore, REST reconciles */
      }
    });
    source.addEventListener('alert', () => {
      setStatus('live');
      setAlertsVersion((v) => v + 1);
    });

    return () => source.close();
  }, []);

  return { status, lastAggregate, alertsVersion };
}
