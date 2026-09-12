// Per-asset return vs. risk contribution (grouped bars), rendered with Plotly.
import Plot from "react-plotly.js";
import type { AttributionResponse } from "../types/portfolio";

export function AttributionChart({ data }: { data: AttributionResponse }) {
  const symbols = data.assets.map((a) => a.symbol);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const traces: any[] = [
    {
      x: symbols,
      y: data.assets.map((a) => a.return_contribution),
      type: "bar",
      name: "Return contribution",
      marker: { color: "#2c7fb8" },
    },
    {
      x: symbols,
      y: data.assets.map((a) => a.risk_contribution),
      type: "bar",
      name: "Risk contribution",
      marker: { color: "#d95f0e" },
    },
  ];

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const layout: any = {
    barmode: "group",
    height: 360,
    margin: { t: 20, r: 10, b: 40, l: 60 },
    yaxis: { title: "Contribution", tickformat: ".2%" },
    legend: { orientation: "h" },
  };

  return (
    <Plot
      data={traces}
      layout={layout}
      style={{ width: "100%" }}
      useResizeHandler
      config={{ displayModeBar: false, responsive: true }}
    />
  );
}
