// Monte Carlo fan chart: median path with a shaded 5th-95th percentile band.
import Plot from "react-plotly.js";
import type { MonteCarloResult } from "../types/portfolio";

export function MonteCarloChart({ result }: { result: MonteCarloResult }) {
  const { t, p5, p50, p95 } = result.bands;

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const traces: any[] = [
    { x: t, y: p95, mode: "lines", line: { width: 0 }, name: "95th pct", showlegend: false },
    {
      x: t,
      y: p5,
      mode: "lines",
      line: { width: 0 },
      fill: "tonexty",
      fillcolor: "rgba(44,127,184,0.2)",
      name: "5–95% band",
    },
    { x: t, y: p50, mode: "lines", line: { color: "#2c7fb8", width: 2 }, name: "Median" },
  ];

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const layout: any = {
    height: 380,
    margin: { t: 20, r: 10, b: 40, l: 60 },
    xaxis: { title: "Trading days" },
    yaxis: { title: "Portfolio value" },
    legend: { orientation: "h" },
  };

  return (
    <div>
      <div className="kpi-row">
        <Kpi label={`VaR ${(result.var_level * 100).toFixed(0)}%`} value={`${(result.var * 100).toFixed(2)}%`} />
        <Kpi label="CVaR" value={`${(result.cvar * 100).toFixed(2)}%`} />
        <Kpi label="P(loss)" value={`${(result.prob_loss * 100).toFixed(1)}%`} />
        <Kpi label="E[terminal]" value={result.expected_terminal_value.toFixed(0)} />
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
