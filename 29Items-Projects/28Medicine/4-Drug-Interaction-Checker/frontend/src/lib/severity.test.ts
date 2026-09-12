import { describe, expect, it } from 'vitest';

import { SEVERITY_LABEL, SEVERITY_ORDER, compareSeverityDesc } from './severity';

describe('severity', () => {
  it('orders severities ascending by risk', () => {
    expect(SEVERITY_ORDER.contraindicated).toBeGreaterThan(SEVERITY_ORDER.major);
    expect(SEVERITY_ORDER.major).toBeGreaterThan(SEVERITY_ORDER.minor);
    expect(SEVERITY_ORDER.minor).toBeGreaterThan(SEVERITY_ORDER.unknown);
  });

  it('sorts most severe first', () => {
    const sorted = (['minor', 'major', 'moderate'] as const).slice().sort(compareSeverityDesc);
    expect(sorted[0]).toBe('major');
    expect(sorted[2]).toBe('minor');
  });

  it('exposes human-readable labels', () => {
    expect(SEVERITY_LABEL.major).toBe('Major');
    expect(SEVERITY_LABEL.contraindicated).toBe('Contraindicated');
  });
});
