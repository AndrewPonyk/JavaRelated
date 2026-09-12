/** Formatting helpers shared across the UI. */

export function money(amount: number, currency = 'EUR'): string {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currency || 'EUR' }).format(amount);
}

export function stars(rating: number): string {
  const full = Math.round(rating);
  return '★★★★★'.slice(0, full) + '☆☆☆☆☆'.slice(0, 5 - full);
}
