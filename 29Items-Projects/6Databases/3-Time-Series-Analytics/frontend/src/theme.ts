/**
 * Chart role tokens (light + dark), from the validated reference palette.
 * Components reference ROLES, never raw hex — swap the brand palette here only.
 * Both mode sets passed the palette validator (lightness band, chroma, CVD
 * separation, ≥3:1 contrast vs surface).
 */

import { useEffect, useState } from "react";

export interface ChartTheme {
  surface: string;
  textPrimary: string;
  textSecondary: string;
  muted: string;
  gridline: string;
  baseline: string;
  series1: string; // categorical slot 1 — the metric line
  statusCritical: string; // reserved status color — anomaly markers ONLY
  statusGood: string; // reserved status color — "enabled" indicators
}

export const lightTheme: ChartTheme = {
  surface: "#fcfcfb",
  textPrimary: "#0b0b0b",
  textSecondary: "#52514e",
  muted: "#898781",
  gridline: "#e1e0d9",
  baseline: "#c3c2b7",
  series1: "#2a78d6",
  statusCritical: "#d03b3b",
  statusGood: "#0ca30c",
};

export const darkTheme: ChartTheme = {
  surface: "#1a1a19",
  textPrimary: "#ffffff",
  textSecondary: "#c3c2b7",
  muted: "#898781",
  gridline: "#2c2c2a",
  baseline: "#383835",
  series1: "#3987e5",
  statusCritical: "#d03b3b",
  statusGood: "#0ca30c",
};

/** Dark mode is a *selected* token set, not an automatic color flip. */
export function useChartTheme(): ChartTheme {
  const [dark, setDark] = useState(() => window.matchMedia("(prefers-color-scheme: dark)").matches);
  useEffect(() => {
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = (event: MediaQueryListEvent) => setDark(event.matches);
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);
  return dark ? darkTheme : lightTheme;
}
