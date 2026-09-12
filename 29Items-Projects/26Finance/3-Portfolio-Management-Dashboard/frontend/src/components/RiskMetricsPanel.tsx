// Risk KPIs plus a D3-rendered horizontal bar chart of the tail-risk metrics.
// D3 fully owns the <svg> via a ref (React never touches its children) — the
// recommended way to mix the two libraries (see docs/TECH-NOTES.md).
import * as d3 from "d3";
import { useEffect, useRef } from "react";
import type { RiskMetrics } from "../types/portfolio";

interface Props {
  metrics: RiskMetrics;
}

const pct = (v: number) => `${(v * 100).toFixed(2)}%`;

export function RiskMetricsPanel({ metrics }: Props) {
  const svgRef = useRef<SVGSVGElement | null>(null);

  useEffect(() => {
    if (!svgRef.current) return;

    const data = [
      { label: "VaR 95% (hist)", value: metrics.var_historical },
      { label: "VaR 95% (param)", value: metrics.var_parametric },
      { label: "CVaR 95%", value: metrics.cvar_historical },
      { label: "Max Drawdown", value: Math.abs(metrics.max_drawdown) },
    ];

    const width = 460;
    const height = 200;
    const margin = { top: 10, right: 50, bottom: 20, left: 140 };

    const svg = d3.select(svgRef.current).attr("viewBox", `0 0 ${width} ${height}`);
    svg.selectAll("*").remove();

    const x = d3
      .scaleLinear()
      .domain([0, d3.max(data, (d) => d.value) ?? 0.1])
      .range([margin.left, width - margin.right]);

    const y = d3
      .scaleBand()
      .domain(data.map((d) => d.label))
      .range([margin.top, height - margin.bottom])
      .padding(0.25);

    svg
      .append("g")
      .selectAll("rect")
      .data(data)
      .join("rect")
      .attr("x", margin.left)
      .attr("y", (d) => y(d.label)!)
      .attr("height", y.bandwidth())
      .attr("width", (d) => x(d.value) - margin.left)
      .attr("fill", "#d9534f");

    svg
      .append("g")
      .selectAll("text")
      .data(data)
      .join("text")
      .attr("x", (d) => x(d.value) + 4)
      .attr("y", (d) => y(d.label)! + y.bandwidth() / 2)
      .attr("dy", "0.35em")
      .attr("font-size", 11)
      .text((d) => pct(d.value));

    svg
      .append("g")
      .attr("transform", `translate(${margin.left},0)`)
      .call(d3.axisLeft(y))
      .attr("font-size", 11);
  }, [metrics]);

  return (
    <div>
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 12 }}>
        <Kpi label="Ann. Return" value={pct(metrics.annualized_return)} />
        <Kpi label="Ann. Volatility" value={pct(metrics.annualized_volatility)} />
        <Kpi label="Sharpe" value={metrics.sharpe_ratio.toFixed(2)} />
        <Kpi label="Sortino" value={metrics.sortino_ratio.toFixed(2)} />
      </div>
      <svg ref={svgRef} style={{ width: "100%", maxWidth: 480 }} />
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        border: "1px solid #e0e0e0",
        borderRadius: 8,
        padding: "8px 14px",
        minWidth: 110,
      }}
    >
      <div style={{ fontSize: 12, color: "#666" }}>{label}</div>
      <div style={{ fontSize: 20, fontWeight: 600 }}>{value}</div>
    </div>
  );
}
