import { useCallback, useEffect, useState } from 'react';
import { acknowledgeAlert, getAlerts } from '../api/client';
import type { AlertSeverity, AnomalyAlert } from '../types/metrics';

const FALLBACK_POLL_MS = 30_000; // SSE 'alert' events drive refreshes; this is the safety net

// Severity is icon + label (+ reserved status color) — never color alone.
const SEVERITY_ICON: Record<AlertSeverity, string> = {
  critical: '▲',
  serious: '◆',
  warning: '●',
};

const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour12: false });

interface Props {
  /** Bumped by the SSE hook whenever a new alert arrives. */
  alertsVersion: number;
}

/** Open anomaly alerts with acknowledge workflow (optimistic update + revert). */
export default function AnomalyAlertsPanel({ alertsVersion }: Props) {
  const [alerts, setAlerts] = useState<AnomalyAlert[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback((signal?: AbortSignal) => {
    getAlerts('open', signal)
      .then((data) => {
        setAlerts(data);
        setError(null);
      })
      .catch((e: unknown) => {
        if (!signal?.aborted) setError(e instanceof Error ? e.message : 'Failed to load alerts');
      });
  }, []);

  useEffect(() => {
    const ctrl = new AbortController();
    load(ctrl.signal);
    const timer = setInterval(() => load(), FALLBACK_POLL_MS);
    return () => {
      ctrl.abort();
      clearInterval(timer);
    };
  }, [load, alertsVersion]);

  const ack = async (alertId: string) => {
    const before = alerts;
    setAlerts((current) => current?.filter((a) => a.alertId !== alertId) ?? null); // optimistic
    try {
      await acknowledgeAlert(alertId);
    } catch (e: unknown) {
      setAlerts(before); // revert
      setError(e instanceof Error ? e.message : 'Acknowledge failed');
    }
  };

  return (
    <section className="panel" aria-label="Anomaly alerts">
      <h2>Open anomaly alerts</h2>

      {error ? (
        <div className="state error" role="alert">
          {error}
          <button onClick={() => load()}>Retry</button>
        </div>
      ) : alerts === null ? (
        <div className="skeleton" aria-label="Loading alerts" />
      ) : alerts.length === 0 ? (
        <div className="state">No open alerts — all metrics within expected ranges.</div>
      ) : (
        <div>
          {alerts.map((a) => (
            <div className="alert-row" key={a.alertId}>
              <span className={`sev sev-${a.severity}`}>
                <span className="dot" aria-hidden="true" />
                {SEVERITY_ICON[a.severity]} {a.severity}
              </span>
              <div className="alert-meta">
                <div className="metric">{a.metricKey}</div>
                <div className="detail">
                  observed {a.observed.toLocaleString()} vs expected{' '}
                  {a.expected.toLocaleString(undefined, { maximumFractionDigits: 1 })} · z=
                  {a.score.toFixed(1)} · {fmtTime(a.detectedAt)}
                </div>
              </div>
              <button className="ack" onClick={() => ack(a.alertId)}>
                Ack
              </button>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
