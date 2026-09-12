/** Device picker with loading/error/empty states and operator actions. */

import { api } from "../api/client";
import { usePolling } from "../hooks/useMetrics";
import { useChartTheme } from "../theme";
import type { Device } from "../types";

interface Props {
  selected: string | null;
  onSelect: (deviceId: string | null) => void;
  canManage: boolean;
  refreshKey?: number;
}

export function DeviceList({ selected, onSelect, canManage, refreshKey = 0 }: Props) {
  const theme = useChartTheme();
  const {
    data: devices,
    error,
    loading,
    refresh,
  } = usePolling<Device[]>(api.listDevices, 60_000, [refreshKey]);

  if (loading) return <p style={{ color: theme.textSecondary }}>Loading devices…</p>;
  if (error) return <p style={{ color: theme.statusCritical }}>Could not load devices: {error}</p>;
  if (!devices || devices.length === 0)
    return <p style={{ color: theme.textSecondary }}>No devices registered yet.</p>;

  const handleToggle = async (device: Device) => {
    try {
      await api.setDeviceEnabled(device.device_id, !device.enabled);
      refresh();
    } catch (err) {
      window.alert(err instanceof Error ? err.message : "Update failed");
    }
  };

  const handleDelete = async (device: Device) => {
    if (!window.confirm(`Delete device "${device.name}" (${device.device_id})?`)) return;
    try {
      await api.deleteDevice(device.device_id);
      if (selected === device.device_id) onSelect(null);
      refresh();
    } catch (err) {
      window.alert(err instanceof Error ? err.message : "Delete failed");
    }
  };

  const actionStyle: React.CSSProperties = {
    padding: "2px 8px",
    marginRight: 6,
    borderRadius: 4,
    border: `1px solid ${theme.gridline}`,
    background: theme.surface,
    color: theme.textSecondary,
    font: "inherit",
    fontSize: 11,
    cursor: "pointer",
  };

  return (
    <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
      {devices.map((device) => {
        const isSelected = device.device_id === selected;
        return (
          <li
            key={device.device_id}
            style={{
              padding: "8px 10px",
              marginBottom: 4,
              borderRadius: 6,
              border: `1px solid ${isSelected ? theme.series1 : theme.gridline}`,
              background: theme.surface,
            }}
          >
            <button
              onClick={() => onSelect(device.device_id)}
              style={{
                display: "block",
                width: "100%",
                textAlign: "left",
                padding: 0,
                border: "none",
                background: "transparent",
                color: theme.textPrimary,
                cursor: "pointer",
                font: "inherit",
              }}
            >
              <strong style={{ fontSize: 13 }}>{device.name}</strong>
              <span style={{ display: "block", fontSize: 11, color: theme.muted }}>
                {device.device_id} · {device.site}
              </span>
              {/* Status = icon + label, never color alone. */}
              <span
                style={{
                  fontSize: 11,
                  color: device.enabled ? theme.statusGood : theme.statusCritical,
                }}
              >
                {device.enabled ? "● enabled" : "■ disabled"}
              </span>
            </button>
            {canManage && (
              <div style={{ marginTop: 6 }}>
                <button onClick={() => void handleToggle(device)} style={actionStyle}>
                  {device.enabled ? "Disable" : "Enable"}
                </button>
                <button
                  onClick={() => void handleDelete(device)}
                  style={{ ...actionStyle, color: theme.statusCritical }}
                >
                  Delete
                </button>
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}
