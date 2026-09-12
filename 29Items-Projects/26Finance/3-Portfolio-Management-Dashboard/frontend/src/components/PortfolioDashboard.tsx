// Tabbed analytics dashboard for a single portfolio: risk, efficient frontier,
// attribution, Monte Carlo (async job), and backtest.
import { useState } from "react";

import { api } from "../api/client";
import { useAttribution, useEfficientFrontier, useRiskMetrics } from "../hooks/usePortfolio";
import type { BacktestResponse, MonteCarloResult } from "../types/portfolio";
import { AsyncBlock } from "./AsyncBlock";
import { AttributionChart } from "./AttributionChart";
import { BacktestChart } from "./BacktestChart";
import { EfficientFrontierChart } from "./EfficientFrontierChart";
import { MonteCarloChart } from "./MonteCarloChart";
import { RiskMetricsPanel } from "./RiskMetricsPanel";

const TABS = ["Risk", "Frontier", "Attribution", "Monte Carlo", "Backtest"] as const;
type Tab = (typeof TABS)[number];

export function PortfolioDashboard({ portfolioId }: { portfolioId: number }) {
  const [tab, setTab] = useState<Tab>("Risk");
  return (
    <div>
      <div className="tabs">
        {TABS.map((t) => (
          <button key={t} className={t === tab ? "tab active" : "tab"} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>
      <div className="tab-body">
        {tab === "Risk" && <RiskTab id={portfolioId} />}
        {tab === "Frontier" && <FrontierTab id={portfolioId} />}
        {tab === "Attribution" && <AttributionTab id={portfolioId} />}
        {tab === "Monte Carlo" && <MonteCarloTab id={portfolioId} />}
        {tab === "Backtest" && <BacktestTab id={portfolioId} />}
      </div>
    </div>
  );
}

function RiskTab({ id }: { id: number }) {
  const s = useRiskMetrics(id);
  return (
    <AsyncBlock loading={s.loading} error={s.error}>
      {s.data && <RiskMetricsPanel metrics={s.data} />}
    </AsyncBlock>
  );
}

function FrontierTab({ id }: { id: number }) {
  const s = useEfficientFrontier(id);
  return (
    <AsyncBlock loading={s.loading} error={s.error}>
      {s.data && <EfficientFrontierChart frontier={s.data} />}
    </AsyncBlock>
  );
}

function AttributionTab({ id }: { id: number }) {
  const s = useAttribution(id);
  return (
    <AsyncBlock loading={s.loading} error={s.error}>
      {s.data && <AttributionChart data={s.data} />}
    </AsyncBlock>
  );
}

function MonteCarloTab({ id }: { id: number }) {
  const [result, setResult] = useState<MonteCarloResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const { job_id } = await api.dispatchMonteCarlo(id, { n_days: 252, n_sims: 10000, seed: 42 });
      // Poll the job until it completes (worker mode) or returns immediately (eager).
      for (let i = 0; i < 40; i++) {
        const job = await api.getJob(job_id);
        if (job.status === "done" && job.result) {
          setResult(job.result);
          return;
        }
        if (job.status === "failed") throw new Error(job.error ?? "Simulation failed");
        await new Promise((r) => setTimeout(r, 500));
      }
      throw new Error("Simulation timed out");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <button onClick={run} disabled={loading}>
        {loading ? "Running…" : "Run simulation (10k paths · 1y)"}
      </button>
      {error && <p className="error">{error}</p>}
      {result && <MonteCarloChart result={result} />}
    </div>
  );
}

function BacktestTab({ id }: { id: number }) {
  const [data, setData] = useState<BacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    setLoading(true);
    setError(null);
    try {
      setData(
        await api.runBacktest(id, {
          lookback: 126,
          rebalance_every: 21,
          objective: "max_sharpe",
          lookback_days: 756,
        }),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <button onClick={run} disabled={loading}>
        {loading ? "Running…" : "Run walk-forward backtest"}
      </button>
      {error && <p className="error">{error}</p>}
      {data && <BacktestChart data={data} />}
    </div>
  );
}
