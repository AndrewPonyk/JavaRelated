/** Main operator view: device picker/management + metric chart + anomaly feed. */

import { useEffect, useState } from "react";
import { AnomalyList } from "../components/AnomalyList";
import { DeviceForm } from "../components/DeviceForm";
import { DeviceList } from "../components/DeviceList";
import { MetricsChart } from "../components/MetricsChart";
import { useMetricCatalog } from "../hooks/useMetrics";
import { useChartTheme } from "../theme";

// Shown until the device has reported anything (then the catalog takes over).
const FALLBACK_METRICS = ["temperature", "humidity", "power_w"];
const RANGES = [
  { label: "Last 6 h", hours: 6 },
  { label: "Last 24 h", hours: 24 },
  { label: "Last 7 d", hours: 168 },
];

interface Props {
  canManage: boolean;
}

export function DashboardPage({ canManage }: Props) {
  const theme = useChartTheme();
  const [deviceId, setDeviceId] = useState<string | null>(null);
  const [metric, setMetric] = useState(FALLBACK_METRICS[0]);
  const [hours, setHours] = useState(6);
  const [deviceListVersion, setDeviceListVersion] = useState(0);

  const { data: catalog } = useMetricCatalog(deviceId);
  const metricOptions = catalog && catalog.length > 0 ? catalog : FALLBACK_METRICS;

  useEffect(() => {
    if (!metricOptions.includes(metric)) setMetric(metricOptions[0]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [metricOptions.join("|")]);

  const controlStyle: React.CSSProperties = {
    padding: "6px 10px",
    borderRadius: 6,
    border: `1px solid ${theme.gridline}`,
    background: theme.surface,
    color: theme.textPrimary,
    font: "inherit",
    fontSize: 13,
  };

  return (
    <main
      style={{
        display: "flex",
        flexWrap: "wrap",
        gap: 24,
        padding: 24,
        alignItems: "flex-start",
      }}
    >
      <aside style={{ width: 260, flexShrink: 0 }}>
        <h2 style={{ fontSize: 14, color: theme.textSecondary, margin: "0 0 12px" }}>Devices</h2>
        <DeviceList
          selected={deviceId}
          onSelect={setDeviceId}
          canManage={canManage}
          refreshKey={deviceListVersion}
        />
        {canManage && <DeviceForm onRegistered={() => setDeviceListVersion((v) => v + 1)} />}
      </aside>

      <section style={{ flexGrow: 1, minWidth: 320 }}>
        {deviceId ? (
          <>
            {/* Filters: one row above the charts. */}
            <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
              <select
                value={metric}
                onChange={(e) => setMetric(e.target.value)}
                style={controlStyle}
              >
                {metricOptions.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
              <select
                value={hours}
                onChange={(e) => setHours(Number(e.target.value))}
                style={controlStyle}
              >
                {RANGES.map((range) => (
                  <option key={range.hours} value={range.hours}>
                    {range.label}
                  </option>
                ))}
              </select>
            </div>

            <MetricsChart deviceId={deviceId} metric={metric} hours={hours} />

            <h2 style={{ fontSize: 14, color: theme.textSecondary, margin: "24px 0 12px" }}>
              Recent anomalies
            </h2>
            <AnomalyList deviceId={deviceId} />
          </>
        ) : (
          <p style={{ color: theme.textSecondary }}>Select a device to view its metrics.</p>
        )}
      </section>
    </main>
  );
}
