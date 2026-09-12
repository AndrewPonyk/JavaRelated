/**
 * ModelMonitoringDashboard
 *
 * Online-learning monitoring UI for the fraud-detection API. Polls the
 * model catalog, A/B configuration and drift endpoints every 30 seconds
 * (paused while the tab is hidden) and composes the four feature panels.
 */
import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError, api } from "../api/client";
import type {
  ABConfig,
  DriftReportRecord,
  DriftSummary,
  ModelListResponse,
} from "../api/types";
import ABConfigPanel from "./ABConfigPanel";
import DriftPanel from "./DriftPanel";
import ModelsTable from "./ModelsTable";
import PredictionForm from "./PredictionForm";

const POLL_INTERVAL_MS = 30_000;

interface PanelState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}

function initial<T>(): PanelState<T> {
  return { data: null, loading: true, error: null };
}

function message(err: unknown): string {
  if (err instanceof ApiError) {
    return err.detail;
  }
  return err instanceof Error ? err.message : String(err);
}

export default function ModelMonitoringDashboard() {
  const [models, setModels] = useState<PanelState<ModelListResponse>>(initial);
  const [abConfig, setAbConfig] = useState<PanelState<ABConfig>>(initial);
  const [drift, setDrift] = useState<PanelState<DriftSummary>>(initial);
  const [reports, setReports] = useState<DriftReportRecord[]>([]);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const refresh = useCallback(async () => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const { signal } = controller;

    setModels((state) => ({ ...state, loading: true }));
    setAbConfig((state) => ({ ...state, loading: true }));
    setDrift((state) => ({ ...state, loading: true }));

    const [modelsResult, abResult, driftResult, reportsResult] = await Promise.allSettled([
      api.getModels(signal),
      api.getABConfig(signal),
      api.getDrift(signal),
      api.getDriftReports(signal),
    ]);
    if (signal.aborted) {
      return;
    }

    setModels(
      modelsResult.status === "fulfilled"
        ? { data: modelsResult.value, loading: false, error: null }
        : (state) => ({ ...state, loading: false, error: message(modelsResult.reason) }),
    );
    setAbConfig(
      abResult.status === "fulfilled"
        ? { data: abResult.value, loading: false, error: null }
        : (state) => ({ ...state, loading: false, error: message(abResult.reason) }),
    );
    setDrift(
      driftResult.status === "fulfilled"
        ? { data: driftResult.value, loading: false, error: null }
        : (state) => ({ ...state, loading: false, error: message(driftResult.reason) }),
    );
    if (reportsResult.status === "fulfilled") {
      setReports(reportsResult.value.items);
    }
    setLastUpdated(new Date());
  }, []);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        void refresh();
      }
    }, POLL_INTERVAL_MS);
    return () => {
      window.clearInterval(timer);
      abortRef.current?.abort();
    };
  }, [refresh]);

  return (
    <>
      <div className="toolbar">
        <span className="muted">
          {lastUpdated
            ? `Updated ${lastUpdated.toLocaleTimeString()} · refreshes every 30 s`
            : "Loading…"}
        </span>
        <button type="button" onClick={() => void refresh()}>
          Refresh
        </button>
      </div>

      <div className="grid">
        <ModelsTable
          data={models.data}
          loading={models.loading}
          error={models.error}
          onChanged={() => void refresh()}
        />
        <ABConfigPanel
          config={abConfig.data}
          loading={abConfig.loading}
          error={abConfig.error}
          onChanged={() => void refresh()}
        />
        <DriftPanel
          drift={drift.data}
          reports={reports}
          loading={drift.loading}
          error={drift.error}
          onEvaluate={() => void refresh()}
        />
        <PredictionForm />
      </div>
    </>
  );
}
