/** Recent anomalies for the selected device (table view — also serves as the
 *  accessible, non-color representation of the chart's anomaly overlay). */

import { useAnomalies } from "../hooks/useMetrics";
import { useChartTheme } from "../theme";

export function AnomalyList({ deviceId }: { deviceId: string }) {
  const theme = useChartTheme();
  const { data: anomalies, error, loading } = useAnomalies(deviceId);

  if (loading) return <p style={{ color: theme.textSecondary }}>Loading anomalies…</p>;
  if (error)
    return <p style={{ color: theme.statusCritical }}>Could not load anomalies: {error}</p>;
  if (!anomalies || anomalies.length === 0)
    return <p style={{ color: theme.textSecondary }}>No anomalies in the last 24 h.</p>;

  const cell: React.CSSProperties = {
    padding: "6px 10px",
    borderBottom: `1px solid ${theme.gridline}`,
    fontSize: 12,
    textAlign: "left",
  };

  return (
    <table style={{ borderCollapse: "collapse", width: "100%", color: theme.textPrimary }}>
      <thead>
        <tr style={{ color: theme.muted }}>
          <th style={cell}>Time (UTC)</th>
          <th style={cell}>Metric</th>
          <th style={cell}>Value</th>
          <th style={cell}>Expected band</th>
          <th style={cell}>Score</th>
          <th style={cell}>Method</th>
        </tr>
      </thead>
      <tbody>
        {anomalies.map((a) => (
          <tr key={`${a.metric}-${a.ts}`}>
            <td style={{ ...cell, fontVariantNumeric: "tabular-nums" }}>
              {new Date(a.ts).toISOString().slice(0, 19).replace("T", " ")}
            </td>
            <td style={cell}>
              <span style={{ color: theme.statusCritical }}>⚠</span> {a.metric}
            </td>
            <td style={{ ...cell, fontVariantNumeric: "tabular-nums" }}>{a.value.toFixed(2)}</td>
            <td style={{ ...cell, fontVariantNumeric: "tabular-nums", color: theme.textSecondary }}>
              {a.lower !== null && a.upper !== null
                ? `${a.lower.toFixed(2)} – ${a.upper.toFixed(2)}`
                : "—"}
            </td>
            <td style={{ ...cell, fontVariantNumeric: "tabular-nums" }}>{a.score.toFixed(1)}</td>
            <td style={{ ...cell, color: theme.textSecondary }}>{a.method}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
