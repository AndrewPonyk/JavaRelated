// Backtest equity curves: optimized strategy vs. equal-weight benchmark.
import Plot from "react-plotly.js";
import type { BacktestResponse } from "../types/portfolio";

export function BacktestChart({ data }: { data: BacktestResponse }) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const traces: any[] = [
    {
      x: data.dates,
      y: data.strategy_equity,
      mode: "lines",
      name: "Strategy",
      line: { color: "#2c7fb8", width: 2 },
    },
    {
      x: data.dates,
      y: data.benchmark_equity,
      mode: "lines",
      name: "Equal-weight",
      line: { color: "#999", width: 1, dash: "dot" },
    },
  ];

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const layout: any = {
    height: 380,
    margin: { t: 20, r: 10, b: 40, l: 60 },
    xaxis: { title: "Date" },
    yaxis: { title: "Growth of $1" },
    legend: { orientation: "h" },
  };

  return (
    <div>
      <div className="kpi-row">
        <Kpi label="Strategy CAGR" value={`${(data.strategy_cagr * 100).toFixed(2)}%`} />
        <Kpi label="Benchmark CAGR" value={`${(data.benchmark_cagr * 100).toFixed(2)}%`} />
        <Kpi label="Sharpe" value={data.strategy_sharpe.toFixed(2)} />
        <Kpi label="Max DD" value={`${(data.strategy_max_drawdown * 100).toFixed(2)}%`} />
        <Kpi label="Rebalances" value={String(data.n_rebalances)} />
      </div>
      <Plot
        data={traces}
        layout={layout}
        style={{ width: "100%" }}
        useResizeHandler
        config={{ displayModeBar: false, responsive: true }}
      />
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="kpi">
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
    </div>
  );
}
