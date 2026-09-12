/** Display helpers for money and rates. */

export function formatMoney(amount: number, currency = 'USD'): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
}

export function formatPercent(fraction: number, maximumFractionDigits = 1): string {
  return new Intl.NumberFormat('en-US', { style: 'percent', maximumFractionDigits }).format(fraction);
}

export function formatDate(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}
