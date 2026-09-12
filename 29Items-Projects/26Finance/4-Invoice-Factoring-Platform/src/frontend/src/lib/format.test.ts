import { describe, expect, it } from 'vitest';
import { formatMoney, formatPercent } from './format';

describe('format', () => {
  it('formats money with currency', () => {
    expect(formatMoney(8250, 'USD')).toBe('$8,250.00');
  });

  it('formats a fraction as a percent', () => {
    expect(formatPercent(0.025)).toBe('2.5%');
  });
});
