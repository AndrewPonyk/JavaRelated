// Efficient-frontier scatter (volatility vs. expected return), colored by
// Sharpe ratio, with the tangency (max-Sharpe) and min-variance portfolios
// highlighted. Rendered with Plotly.
import Plot from "react-plotly.js";
import type { FrontierResponse } from "../types/portfolio";

interface Props {
  frontier: FrontierResponse;
}

export function EfficientFrontierChart({ frontier }: Props) {
  const xs = frontier.points.map((p) => p.volatility);
  const ys = frontier.points.map((p) => p.expected_return);
  const sharpe = frontier.points.map((p) => p.sharpe);

  // Plotly's trace/layout typings vary across the dist-min build, so we keep
  // these objects loosely typed and rely on Plotly's runtime validation.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const data: any[] = [
    {
      x: xs,
      y: ys,
      mode: "markers",
      type: "scatter",
      name: "Frontier",
      marker: {
        size: 8,
        color: sharpe,
        colorscale: "Viridis",
        showscale: true,
        colorbar: { title: "Sharpe" },
      },
    },
    {
      x: [frontier.max_sharpe.volatility],
      y: [frontier.max_sharpe.expected_return],
      mode: "markers",
      type: "scatter",
      name: "Max Sharpe",
      marker: { size: 16, color: "#d9534f", symbol: "star" },
    },
    {
      x: [frontier.min_variance.volatility],
      y: [frontier.min_variance.expected_return],
      mode: "markers",
      type: "scatter",
      name: "Min Variance",
      marker: { size: 14, color: "#0275d8", symbol: "diamond" },
    },
  ];

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const layout: any = {
    autosize: true,
    height: 460,
    margin: { t: 30, r: 10, b: 50, l: 60 },
    xaxis: { title: "Volatility (annualized)", tickformat: ".0%" },
    yaxis: { title: "Expected Return (annualized)", tickformat: ".0%" },
    legend: { orientation: "h" },
  };

  return (
    <Plot
      data={data}
      layout={layout}
      style={{ width: "100%" }}
      useResizeHandler
      config={{ displayModeBar: false, responsive: true }}
    />
  );
}
