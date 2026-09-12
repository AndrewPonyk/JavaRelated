import type { Severity } from '../types';

export const SEVERITY_ORDER: Record<Severity, number> = {
  unknown: 0,
  minor: 1,
  moderate: 2,
  major: 3,
  contraindicated: 4,
};

export const SEVERITY_COLOR: Record<Severity, string> = {
  unknown: '#9e9e9e',
  minor: '#4caf50',
  moderate: '#ff9800',
  major: '#f44336',
  contraindicated: '#b71c1c',
};

export const SEVERITY_LABEL: Record<Severity, string> = {
  unknown: 'Unknown',
  minor: 'Minor',
  moderate: 'Moderate',
  major: 'Major',
  contraindicated: 'Contraindicated',
};

/** Sort comparator: most severe first. */
export function compareSeverityDesc(a: Severity, b: Severity): number {
  return SEVERITY_ORDER[b] - SEVERITY_ORDER[a];
}
