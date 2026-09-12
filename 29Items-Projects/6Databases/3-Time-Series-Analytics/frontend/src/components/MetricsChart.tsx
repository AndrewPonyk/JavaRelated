/**
 * Time-series line with anomaly overlay + accessible table view.
 * Mark spec: 2px line, no per-point dots; anomalies are ≥8px markers in the
 * reserved status-critical color with a 2px surface ring; hover crosshair +
 * tooltip on by default; identity via swatch+text legend, never color alone.
 */

import { useState } from "react";
import {
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useAnomalies, useMetrics } from "../hooks/useMetrics";
import { useChartTheme } from "../theme";
import type { Anomaly } from "../types";

interface Props {
  deviceId: string;
  metric: string;
  hours?: number;
}

const TABLE_ROW_LIMIT = 200;

const timeFormat = (ms: number) =>
  new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

export function MetricsChart({ deviceId, metric, hours = 6 }: Props) {
  const theme = useChartTheme();
  const [view, setView] = useState<"chart" | "table">("chart");
  const { data: series, error, loading } = useMetrics(deviceId, metric, hours);
  const { data: anomalies } = useAnomalies(deviceId);

  if (loading) return <Status text="Loading series…" color={theme.textSecondary} />;
  if (error)
    return <Status text={`Could not load series: ${error}`} color={theme.statusCritical} />;
  if (!series || series.points.length === 0)
    return <Status text="No data in the selected range." color={theme.textSecondary} />;

  const linePoints = series.points.map((p) => ({ ts: Date.parse(p.ts), value: p.value }));
  const anomalyPoints = (anomalies ?? [])
    .filter((a: Anomaly) => a.metric === metric)
    .map((a) => ({ ts: Date.parse(a.ts), value: a.value, score: a.score, method: a.method }));

  const toggleStyle: React.CSSProperties = {
    marginLeft: "auto",
    padding: "3px 10px",
    borderRadius: 6,
    border: `1px solid ${theme.gridline}`,
    background: theme.surface,
    color: theme.textSecondary,
    font: "inherit",
    fontSize: 12,
    cursor: "pointer",
  };

  return (
    <figure style={{ margin: 0 }}>
      {/* Legend: swatch + text so identity never rides on color alone. */}
      <figcaption
        style={{
          display: "flex",
          gap: 16,
          alignItems: "center",
          fontSize: 13,
          color: theme.textSecondary,
          marginBottom: 8,
        }}
      >
        <span>
          <span
            style={{
              display: "inline-block",
              width: 14,
              height: 3,
              background: theme.series1,
              verticalAlign: "middle",
              marginRight: 6,
            }}
          />
          {metric} ({series.source}
          {series.decimated ? ", decimated" : ""})
        </span>
        <span>
          <span
            style={{
              display: "inline-block",
              width: 9,
              height: 9,
              borderRadius: "50%",
              background: theme.statusCritical,
              border: `2px solid ${theme.surface}`,
              verticalAlign: "middle",
              marginRight: 6,
            }}
          />
          ⚠ anomaly
        </span>
        <button onClick={() => setView(view === "chart" ? "table" : "chart")} style={toggleStyle}>
          {view === "chart" ? "View as table" : "View as chart"}
        </button>
      </figcaption>

      {view === "chart" ? (
        <div style={{ width: "100%", height: 320, background: theme.surface, borderRadius: 8 }}>
          <ResponsiveContainer>
            <ComposedChart data={linePoints} margin={{ top: 16, right: 16, bottom: 4, left: 0 }}>
              <CartesianGrid stroke={theme.gridline} vertical={false} />
              <XAxis
                dataKey="ts"
                type="number"
                scale="time"
                domain={["dataMin", "dataMax"]}
                tickFormatter={timeFormat}
                stroke={theme.baseline}
                tick={{ fill: theme.muted, fontSize: 11 }}
                tickLine={false}
              />
              <YAxis
                stroke="transparent"
                tick={{ fill: theme.muted, fontSize: 11 }}
                tickLine={false}
                width={48}
              />
              <Tooltip
                cursor={{ stroke: theme.baseline, strokeWidth: 1 }}
                labelFormatter={(ms) => new Date(Number(ms)).toLocaleString()}
                contentStyle={{
                  background: theme.surface,
                  border: `1px solid ${theme.gridline}`,
                  borderRadius: 6,
                  color: theme.textPrimary,
                  fontSize: 12,
                }}
              />
              <Line
                dataKey="value"
                name={metric}
                type="monotone"
                stroke={theme.series1}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 5, stroke: theme.surface, strokeWidth: 2 }}
                isAnimationActive={false}
              />
              <Scatter
                data={anomalyPoints}
                dataKey="value"
                name="anomaly"
                fill={theme.statusCritical}
                stroke={theme.surface}
                strokeWidth={2}
                isAnimationActive={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <SeriesTable
          points={linePoints}
          metric={metric}
          textPrimary={theme.textPrimary}
          muted={theme.muted}
          gridline={theme.gridline}
        />
      )}
    </figure>
  );
}

function SeriesTable({
  points,
  metric,
  textPrimary,
  muted,
  gridline,
}: {
  points: { ts: number; value: number }[];
  metric: string;
  textPrimary: string;
  muted: string;
  gridline: string;
}) {
  const visible = points.slice(-TABLE_ROW_LIMIT);
  const cell: React.CSSProperties = {
    padding: "4px 10px",
    borderBottom: `1px solid ${gridline}`,
    fontSize: 12,
    textAlign: "left",
    fontVariantNumeric: "tabular-nums",
  };
  return (
    <div style={{ maxHeight: 320, overflowY: "auto" }}>
      <table style={{ borderCollapse: "collapse", width: "100%", color: textPrimary }}>
        <caption style={{ fontSize: 11, color: muted, textAlign: "left", padding: "4px 0" }}>
          {visible.length < points.length
            ? `Showing newest ${visible.length} of ${points.length} points`
            : `${points.length} points`}
        </caption>
        <thead>
          <tr style={{ color: muted }}>
            <th style={cell}>Time (UTC)</th>
            <th style={cell}>{metric}</th>
          </tr>
        </thead>
        <tbody>
          {visible.map((p) => (
            <tr key={p.ts}>
              <td style={cell}>{new Date(p.ts).toISOString().slice(0, 19).replace("T", " ")}</td>
              <td style={cell}>{p.value.toFixed(3)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Status({ text, color }: { text: string; color: string }) {
  return (
    <div style={{ padding: 32, fontSize: 14, color }} role="status">
      {text}
    </div>
  );
}
