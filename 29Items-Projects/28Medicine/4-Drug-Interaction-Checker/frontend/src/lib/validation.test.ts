import { describe, expect, it } from 'vitest';

import { canAddDrug, normalizeDrugName, validateDrugList } from './validation';

describe('normalizeDrugName', () => {
  it('trims and collapses whitespace', () => {
    expect(normalizeDrugName('  war   farin ')).toBe('war farin');
  });
});

describe('canAddDrug', () => {
  it('rejects empty input', () => {
    expect(canAddDrug([], '   ').ok).toBe(false);
  });

  it('rejects names that are too short', () => {
    expect(canAddDrug([], 'a').ok).toBe(false);
  });

  it('rejects duplicates case-insensitively', () => {
    const result = canAddDrug(['Aspirin'], 'aspirin');
    expect(result.ok).toBe(false);
    expect(result.reason).toContain('already');
  });

  it('accepts a valid new drug', () => {
    expect(canAddDrug(['aspirin'], 'warfarin').ok).toBe(true);
  });
});

describe('validateDrugList', () => {
  it('requires at least two drugs', () => {
    expect(validateDrugList([]).valid).toBe(false);
    expect(validateDrugList(['a']).valid).toBe(false);
    expect(validateDrugList(['a', 'b']).valid).toBe(true);
  });
});
